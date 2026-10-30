let mannAccount = null;
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));

async function csrfToken() {
  const response = await fetch('/api/auth/csrf');
  if (!response.ok) throw new Error('Could not start a secure request.');
  return (await response.json()).token;
}

async function apiWrite(url, data, method = 'POST') {
  const token = await csrfToken();
  const response = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
    body: data === undefined ? undefined : JSON.stringify(data)
  });
  if (!response.ok) {
    let message = 'Request could not be completed.';
    try { message = (await response.json()).message || message; } catch {}
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}

function showAccount() {
  $('#loginBtn').textContent = mannAccount ? 'My dashboard' : 'Log in';
}

async function refreshMannAccount() {
  try {
    const response = await fetch('/api/auth/me');
    if (!response.ok) { mannAccount = null; showAccount(); return; }
    mannAccount = await response.json();
    showAccount();
    const savedResponse = await fetch('/api/saved-plots');
    if (savedResponse.ok) {
      savedIds.clear();
      (await savedResponse.json()).forEach(plot => savedIds.add(plot.id));
      renderListings();
      updateCompare();
    }
  } catch { mannAccount = null; showAccount(); }
}

function persistentLogin() {
  if (mannAccount) { persistentDashboard(); return; }
  let register = false;
  const draw = () => {
    openModal(`<form class="contact-form" id="persistentAuthForm">
      <div class="eyebrow"><span class="eyebrow-line"></span> ${register ? 'CREATE YOUR ACCOUNT' : 'WELCOME TO MANNVEEDU'}</div>
      <h2 id="modalTitle">${register ? 'Start your land journey.' : 'A place to begin.'}</h2>
      <p>${register ? 'Create a buyer or owner account. Your password is stored as a secure hash.' : 'Log in to save plots, compare your shortlist, and manage your requests.'}</p>
      <div class="form-grid">
        ${register ? '<label class="full">Full name<input name="fullName" placeholder="Your name" required maxlength="120"></label>' : ''}
        <label class="full">Email address<input name="email" type="email" placeholder="you@example.com" required></label>
        ${register ? '<label class="full">Phone number<input name="phone" type="tel" placeholder="+91 98765 43210" maxlength="30"></label>' : ''}
        <label class="full">Password<input name="password" type="password" placeholder="${register ? 'At least 8 characters' : 'Your password'}" minlength="${register ? '8' : '1'}" required></label>
        ${register ? '<label class="full">I’m here as<select name="role"><option value="BUYER">A buyer</option><option value="OWNER">A land owner</option></select></label>' : ''}
      </div>
      <p class="auth-error hidden" id="authError"></p>
      <button class="button button-green" type="submit">${register ? 'Create account' : 'Log in'} <span>↗</span></button>
      <button class="auth-switch" id="authSwitch" type="button">${register ? 'Already have an account? Log in' : 'New to MannVeedu? Create an account'}</button>
    </form>`);
    $('#authSwitch').onclick = () => { register = !register; draw(); };
    $('#persistentAuthForm').onsubmit = async event => {
      event.preventDefault();
      const data = Object.fromEntries(new FormData(event.currentTarget));
      const button = event.currentTarget.querySelector('[type=submit]');
      button.disabled = true;
      button.textContent = register ? 'Creating account…' : 'Logging in…';
      try {
        mannAccount = await apiWrite(register ? '/api/auth/register' : '/api/auth/login', data);
        showAccount();
        await refreshMannAccount();
        persistentDashboard();
      } catch (error) {
        const errorBox = $('#authError');
        errorBox.textContent = error.message;
        errorBox.classList.remove('hidden');
        button.disabled = false;
        button.innerHTML = `${register ? 'Create account' : 'Log in'} <span>↗</span>`;
      }
    };
  };
  draw();
}

async function persistentDashboard() {
  if (!mannAccount) { persistentLogin(); return; }
  let items = [], enquiries = [], reviews = [], pending = [], allEnquiries = [], supportQueries = [], prebookings=[];
  try {
    const [itemsResponse,enquiriesResponse,reviewsResponse,pendingResponse,adminResponse,supportResponse,prebookingResponse]=await Promise.all([
      fetch(mannAccount.role === 'OWNER' ? '/api/my-plots' : '/api/saved-plots'),fetch('/api/enquiries/mine'),
      mannAccount.role==='OWNER'?fetch('/api/my-plots/review-status'):Promise.resolve(null),
      mannAccount.role==='ADMIN'?fetch('/api/admin/listings/pending'):Promise.resolve(null),
      mannAccount.role==='ADMIN'?fetch('/api/admin/enquiries'):Promise.resolve(null),
      mannAccount.role==='ADMIN'?fetch('/api/admin/support-queries'):Promise.resolve(null),
      mannAccount.role==='OWNER'?fetch('/api/prebookings/owner'):mannAccount.role==='BUYER'?fetch('/api/prebookings/mine'):Promise.resolve(null)
    ]);
    if(itemsResponse.ok)items=await itemsResponse.json();
    if(enquiriesResponse.ok)enquiries=await enquiriesResponse.json();
    if(reviewsResponse?.ok)reviews=await reviewsResponse.json();
    if(pendingResponse?.ok)pending=await pendingResponse.json();
    if(adminResponse?.ok)allEnquiries=await adminResponse.json();
    if(supportResponse?.ok)supportQueries=await supportResponse.json();
    if(prebookingResponse?.ok)prebookings=await prebookingResponse.json();
  } catch {}
  const owner=mannAccount.role==='OWNER';
  const reviewById=new Map(reviews.map(row=>[row.plot.id,row.reviewStatus]));
  const listingMarkup=items.length?items.map(plot=>owner
    ?`<div class="owner-listing-row"><button class="dashboard-item" data-dash-plot="${escapeHtml(plot.id)}"><img src="${escapeHtml(plot.image)}" alt=""><span><b>${escapeHtml(plot.title)}</b><small>${escapeHtml(plot.locality)}, ${escapeHtml(plot.city)} · ${money(plot.price)} · ${escapeHtml(reviewById.get(plot.id)||'PENDING')}</small></span><i>↗</i></button><div class="listing-actions"><button class="auth-switch" data-edit-plot="${escapeHtml(plot.id)}">Edit</button><button class="auth-switch danger-button" data-delete-plot="${escapeHtml(plot.id)}">Remove</button><button class="auth-switch" data-manage-photos="${escapeHtml(plot.id)}">Photos</button></div></div>`
    :`<button class="dashboard-item" data-dash-plot="${escapeHtml(plot.id)}"><img src="${escapeHtml(plot.image)}" alt=""><span><b>${escapeHtml(plot.title)}</b><small>${escapeHtml(plot.locality)}, ${escapeHtml(plot.city)} · ${money(plot.price)}</small></span><i>↗</i></button>`).join('')
    :`<div class="dashboard-empty"><b>${owner?'Your listings will appear here.':'Your shortlist is ready when you are.'}</b><small>${owner?'Add a land listing to publish it to the marketplace.':'Save a plot from Explore land and it will appear here.'}</small></div>`;
  const visitLabels={NEW:'requested',CONFIRMED:'confirmed',RESCHEDULED:'date proposed',CHANGE_REQUESTED:'client proposed another date',COMPLETED:'completed',CANCELLED:'cancelled'};
  const visitCard=item=>`<article class="enquiry-item"><div><b>Site visit ${visitLabels[item.status]||'updated'} · ${escapeHtml(item.plotTitle)}</b><small>${escapeHtml(item.locality)}, ${escapeHtml(item.city)} · ${escapeHtml(item.createdAt?.replace('T',' ').slice(0,16)||'Recently submitted')}</small></div><p><b>${escapeHtml(item.name)}</b> · ${escapeHtml(item.phone)}${item.email?` · ${escapeHtml(item.email)}`:''}</p>${item.preferredDate?`<small>Visit date: ${escapeHtml(item.preferredDate)}</small>`:''}${item.status==='RESCHEDULED'?'<small>Waiting for the client to accept this date.</small>':''}${item.status==='CHANGE_REQUESTED'?'<small>The client suggested this date. Please confirm it or suggest another.</small>':''}${item.message?`<p>${escapeHtml(item.message)}</p>`:''}<div class="visit-actions">${['NEW','CHANGE_REQUESTED'].includes(item.status)?`<button data-visit-status="CONFIRMED" data-visit-id="${item.id}">${item.status==='NEW'?'Confirm visit':'Accept client date'}</button>`:''}${['NEW','RESCHEDULED','CHANGE_REQUESTED','CONFIRMED'].includes(item.status)?`<button data-reschedule-visit="${item.id}">${item.status==='NEW'?'Offer another date':'Suggest date'}</button><button data-visit-status="CANCELLED" data-visit-id="${item.id}">Cancel</button>`:''}${item.status==='CONFIRMED'?`<button data-visit-status="COMPLETED" data-visit-id="${item.id}">Complete</button>`:''}</div></article>`;
  const enquiryCard=item=>`<article class="enquiry-item"><div><b>Property enquiry · ${escapeHtml(item.plotTitle)}</b><small>${escapeHtml(item.locality)}, ${escapeHtml(item.city)} · ${escapeHtml(item.createdAt?.replace('T',' ').slice(0,16)||'Recently submitted')}</small></div><p><b>${escapeHtml(item.name)}</b> · ${escapeHtml(item.phone)}${item.email?` · ${escapeHtml(item.email)}`:''}</p>${item.message?`<p>${escapeHtml(item.message)}</p>`:''}</article>`;
  const section=(title,rows,render,empty)=>`<div class="dashboard-section"><div class="dashboard-section-title">${title} (${rows.length})</div><div class="enquiry-list">${rows.length?rows.map(render).join(''):`<div class="dashboard-empty"><small>${empty}</small></div>`}</div></div>`;
  const siteVisits=enquiries.filter(item=>item.requestType==='SITE_VISIT');
  const activeVisits=siteVisits.filter(item=>item.status!=='CANCELLED');
  const requestedVisits=activeVisits.filter(item=>['NEW','RESCHEDULED','CHANGE_REQUESTED'].includes(item.status));
  const confirmedVisits=activeVisits.filter(item=>item.status==='CONFIRMED');
  const completedVisits=activeVisits.filter(item=>item.status==='COMPLETED');
  const contactEnquiries=enquiries.filter(item=>item.requestType!=='SITE_VISIT');
  const buyerVisits=enquiries.filter(item=>item.requestType==='SITE_VISIT');
  const upcomingBuyerVisits=buyerVisits.filter(item=>!['COMPLETED','CANCELLED'].includes(item.status));
  const pastBuyerVisits=buyerVisits.filter(item=>['COMPLETED','CANCELLED'].includes(item.status));
  const buyerContactEnquiries=enquiries.filter(item=>item.requestType!=='SITE_VISIT');
  const buyerVisitCard=item=>`<article class="enquiry-item"><b>Site visit ${visitLabels[item.status]||'updated'} · ${escapeHtml(item.plotTitle)}</b><small>${escapeHtml(item.locality)}, ${escapeHtml(item.city)} · ${escapeHtml(item.createdAt?.replace('T',' ').slice(0,16)||'Recently submitted')}</small>${item.preferredDate?`<small>Visit date: ${escapeHtml(item.preferredDate)}</small>`:''}${item.status==='RESCHEDULED'?`<small>The owner suggested a new date. Accept it or suggest a date that works for you.</small><div class="visit-actions"><button data-buyer-visit-response="CONFIRMED" data-visit-id="${item.id}">Accept date</button><button data-buyer-reschedule="${item.id}">Suggest another date</button><button data-buyer-visit-response="CANCELLED" data-visit-id="${item.id}">Cancel visit</button></div>`:''}${item.status==='NEW'?'<small>Your request is waiting for the owner to respond.</small>':''}${item.status==='CHANGE_REQUESTED'?'<small>Your suggested date is waiting for the owner to confirm.</small>':''}${item.status==='CONFIRMED'?'<small>This date is agreed. You can cancel if your plans change.</small>':''}${['NEW','CHANGE_REQUESTED','CONFIRMED'].includes(item.status)?`<div class="visit-actions"><button data-buyer-visit-response="CANCELLED" data-visit-id="${item.id}">Cancel visit</button></div>`:''}${item.status==='CANCELLED'?'<small>This visit was cancelled.</small>':''}${item.status==='COMPLETED'?'<small>This visit is finished and kept here as history.</small>':''}${item.message?`<p>${escapeHtml(item.message)}</p>`:''}</article>`;
  const prebookingCard=item=>`<article class="enquiry-item"><b>Pre-booking ${item.status.toLowerCase()} · ${escapeHtml(item.plotTitle)}</b><small>${escapeHtml(item.locality)}, ${escapeHtml(item.city)} · ${escapeHtml(item.createdAt?.replace('T',' ').slice(0,16)||'Recently requested')}</small><small>${item.status==='PENDING'?'Waiting for the listing owner to review this request.':item.status==='APPROVED'?'The owner approved your request. The land is held for 48 hours.':item.status==='DECLINED'?'The owner could not approve this request.':item.status==='EXPIRED'?'The 48-hour hold ended; the land is available again.':'This pre-booking is no longer active.'}</small>${item.status==='APPROVED'&&item.expiresAt?`<small>Hold ends: ${escapeHtml(item.expiresAt.replace('T',' ').slice(0,16))}</small>`:''}${item.buyerName?`<p><b>${escapeHtml(item.buyerName)}</b> · ${escapeHtml(item.phone||'')}${item.buyerEmail?` · ${escapeHtml(item.buyerEmail)}`:''}</p>`:''}${item.message?`<p>${escapeHtml(item.message)}</p>`:''}<div class="visit-actions">${owner&&item.status==='PENDING'?`<button data-prebooking-decision="APPROVED" data-prebooking-id="${item.id}">Approve & hold land</button><button data-prebooking-decision="DECLINED" data-prebooking-id="${item.id}">Decline</button>`:''}${owner&&item.status==='APPROVED'?`<button data-prebooking-decision="CANCELLED" data-prebooking-id="${item.id}">Release land</button>`:''}${!owner&&['PENDING','APPROVED'].includes(item.status)?`<button data-prebooking-cancel="${item.id}">Cancel request</button>`:''}</div></article>`;
  const adminMarkup=pending.length?pending.map(row=>`<article class="enquiry-item"><div><b>${escapeHtml(row.plot.title)}</b><small>${escapeHtml(row.plot.locality)}, ${escapeHtml(row.plot.city)} · ${escapeHtml(row.ownerName||row.ownerEmail||'Unknown owner')} · ${money(row.plot.price)}</small></div><div class="visit-actions"><button data-review="APPROVED" data-review-id="${escapeHtml(row.plot.id)}">Approve</button><button data-review="REJECTED" data-review-id="${escapeHtml(row.plot.id)}">Reject</button></div></article>`).join(''):`<div class="dashboard-empty"><b>No listings waiting for review.</b><small>New owner submissions will appear here.</small></div>`;
  const adminEnquiryMarkup=allEnquiries.length?allEnquiries.map(item=>`<article class="enquiry-item"><b>${item.requestType==='SITE_VISIT'?'Site visit':'Enquiry'} · ${escapeHtml(item.plotTitle)}</b><small>${escapeHtml(item.name)} · ${escapeHtml(item.phone)} · ${escapeHtml(item.email||item.buyerEmail||'Guest')} · ${escapeHtml(item.status)}</small></article>`).join(''):`<div class="dashboard-empty"><b>No enquiries have been submitted yet.</b></div>`;
  const supportLabels={OPEN:'Open',IN_PROGRESS:'In progress',RESOLVED:'Resolved'};
  const supportMarkup=supportQueries.length?supportQueries.map(item=>`<article class="enquiry-item"><b>${escapeHtml(item.topic)} · #${item.id} <span class="support-status support-${item.status.toLowerCase()}">${supportLabels[item.status]||'Open'}</span></b><small>${escapeHtml(item.name)} · <a href="mailto:${escapeHtml(item.email)}">${escapeHtml(item.email)}</a> · ${escapeHtml(item.createdAt?.replace('T',' ').slice(0,16)||'Recently received')}</small><p>${escapeHtml(item.message)}</p><div class="visit-actions"><button data-support-status="OPEN" data-support-id="${item.id}">Open</button><button data-support-status="IN_PROGRESS" data-support-id="${item.id}">In progress</button><button data-support-status="RESOLVED" data-support-id="${item.id}">Resolve</button></div></article>`).join(''):`<div class="dashboard-empty"><b>No support queries yet.</b></div>`;
  openModal(`<div class="compare-modal">
    <div class="eyebrow"><span class="eyebrow-line"></span> YOUR MANNVEEDU</div>
    <h2 id="modalTitle">${mannAccount.role==='ADMIN'?'Admin dashboard':owner?'Your land dashboard':'Your buyer dashboard'}</h2>
    <p>Signed in as ${escapeHtml(mannAccount.fullName)} · ${owner ? 'Land owner' : escapeHtml(mannAccount.role.toLowerCase())}</p>
    ${mannAccount.role==='ADMIN'?`<div class="dashboard-section"><div class="dashboard-section-title">Listings awaiting approval (${pending.length})</div><div class="enquiry-list">${adminMarkup}</div></div><div class="dashboard-section"><div class="dashboard-section-title">All enquiries and site visits (${allEnquiries.length})</div><div class="enquiry-list">${adminEnquiryMarkup}</div></div><div class="dashboard-section"><div class="dashboard-section-title">Support queries (${supportQueries.length})</div><div class="enquiry-list">${supportMarkup}</div></div>`:`<div class="dashboard-section"><div class="dashboard-section-title">${owner?'Your land listings':'Saved plots'}</div><div class="dashboard-list">${listingMarkup}</div></div>`}
    ${owner ? '<button class="button button-green" id="addPlotBtn">List another plot <span>↗</span></button>' : ''}
    ${owner?`${section('New & rescheduled visits',requestedVisits,visitCard,'No new visit requests.')}${section('Confirmed visits',confirmedVisits,visitCard,'Confirmed visits will appear here.')}${section('Completed visits',completedVisits,visitCard,'No completed visits yet.')}${section('Pre-booking requests',prebookings,prebookingCard,'No buyers have requested a pre-booking yet.')}${section('Other enquiries',contactEnquiries,enquiryCard,'No other enquiries yet.')}`:`${section('Upcoming & pending site visits',upcomingBuyerVisits,buyerVisitCard,'No upcoming or pending visits.')}${section('Past site visits',pastBuyerVisits,buyerVisitCard,'Past visits will appear here when finished or cancelled.')}${section('Your pre-bookings',prebookings,prebookingCard,'You have not requested a pre-booking yet.')}${section('Other enquiries',buyerContactEnquiries,enquiryCard,'No other enquiries yet.')}`}
    <button class="button button-dark" id="logoutBtn">Sign out</button>
    <p class="form-note">Your land activity and requests are available here whenever you sign in.</p>
  </div>`);
  $('#modalContent').querySelectorAll('[data-dash-plot]').forEach(button => button.onclick = () => {
    const plot=items.find(item=>item.id===button.dataset.dashPlot);
    if(plots.some(item=>item.id===button.dataset.dashPlot))openDetail(button.dataset.dashPlot);
    else if(plot)openModal(`<div class="compare-modal"><div class="eyebrow"><span class="eyebrow-line"></span> LISTING REVIEW</div><h2 id="modalTitle">${escapeHtml(plot.title)}</h2><p>This listing is ${escapeHtml(reviewById.get(plot.id)||'PENDING').toLowerCase()} admin approval and is hidden from public search until approved.</p><p>${escapeHtml(plot.locality)}, ${escapeHtml(plot.city)} · ${money(plot.price)}</p><button class="button button-dark" id="privatePlotDone">Back to dashboard</button></div>`);
    $('#privatePlotDone')?.addEventListener('click',persistentDashboard);
  });
  $('#modalContent').querySelectorAll('[data-edit-plot]').forEach(button=>button.onclick=()=>openPlotForm(items.find(plot=>plot.id===button.dataset.editPlot)));
  $('#modalContent').querySelectorAll('[data-manage-photos]').forEach(button=>button.onclick=()=>openPhotoForm(button.dataset.managePhotos));
  $('#modalContent').querySelectorAll('[data-review]').forEach(button=>button.onclick=async()=>{try{await apiWrite(`/api/admin/listings/${encodeURIComponent(button.dataset.reviewId)}/review`,{status:button.dataset.review},'PUT');toast(`Listing ${button.dataset.review.toLowerCase()}`);persistentDashboard();loadPlots();}catch(e){toast(e.message)}});
  $('#modalContent').querySelectorAll('[data-visit-status]').forEach(button=>button.onclick=async()=>{const labels={CONFIRMED:'confirmed',COMPLETED:'completed',CANCELLED:'cancelled'};try{await apiWrite(`/api/enquiries/${button.dataset.visitId}/visit`,{status:button.dataset.visitStatus,preferredDate:null},'PUT');toast(`Site visit ${labels[button.dataset.visitStatus]}`);persistentDashboard()}catch(e){toast(e.message)}});
  $('#modalContent').querySelectorAll('[data-reschedule-visit]').forEach(button=>button.onclick=()=>openRescheduleForm(button.dataset.rescheduleVisit));
  $('#modalContent').querySelectorAll('[data-buyer-visit-response]').forEach(button=>button.onclick=async()=>{try{await apiWrite(`/api/enquiries/${button.dataset.visitId}/buyer-response`,{status:button.dataset.buyerVisitResponse,preferredDate:null},'PUT');toast(button.dataset.buyerVisitResponse==='CONFIRMED'?'Visit date confirmed':'Visit cancelled');persistentDashboard()}catch(e){toast(e.message)}});
  $('#modalContent').querySelectorAll('[data-prebooking-decision]').forEach(button=>button.onclick=async()=>{try{await apiWrite(`/api/prebookings/${button.dataset.prebookingId}/owner`,{status:button.dataset.prebookingDecision},'PUT');toast(button.dataset.prebookingDecision==='APPROVED'?'Pre-booking approved; land is on hold':button.dataset.prebookingDecision==='DECLINED'?'Pre-booking declined':'Land released');persistentDashboard();loadPlots()}catch(e){toast(e.message)}});
  $('#modalContent').querySelectorAll('[data-prebooking-cancel]').forEach(button=>button.onclick=async()=>{if(!confirm('Cancel this pre-booking request?'))return;try{await apiWrite(`/api/prebookings/${button.dataset.prebookingCancel}/cancel`,{},'PUT');toast('Pre-booking cancelled');persistentDashboard();loadPlots()}catch(e){toast(e.message)}});
  $('#modalContent').querySelectorAll('[data-buyer-reschedule]').forEach(button=>button.onclick=()=>openRescheduleForm(button.dataset.buyerReschedule,true));
  $('#modalContent').querySelectorAll('[data-support-status]').forEach(button=>button.onclick=async()=>{try{await apiWrite(`/api/admin/support-queries/${button.dataset.supportId}/status`,{status:button.dataset.supportStatus},'PUT');toast(`Support query marked ${button.dataset.supportStatus==='IN_PROGRESS'?'in progress':button.dataset.supportStatus.toLowerCase()}`);persistentDashboard()}catch(e){toast(e.message)}});
  $('#modalContent').querySelectorAll('[data-delete-plot]').forEach(button=>button.onclick=async()=>{
    const plot=items.find(item=>item.id===button.dataset.deletePlot);if(!plot||!confirm(`Remove “${plot.title}” from your active listings?`))return;
    try{await apiWrite(`/api/plots/${encodeURIComponent(plot.id)}`,undefined,'DELETE');plots=plots.filter(item=>item.id!==plot.id);applyFilters();persistentDashboard();toast('Listing removed');}
    catch(error){toast(error.message)}
  });
  $('#logoutBtn').onclick = async () => {
    try { await apiWrite('/api/auth/logout'); } catch {}
    mannAccount = null;
    savedIds.clear();
    renderListings();
    showAccount();
    closeModal();
    toast('Signed out');
  };
  $('#addPlotBtn')?.addEventListener('click', () => openPlotForm());
}

async function uploadPhotos(plotId,files) {
  const token=await csrfToken(),data=new FormData();[...files].forEach(file=>data.append('files',file));
  const response=await fetch(`/api/plots/${encodeURIComponent(plotId)}/photos`,{method:'POST',headers:{'X-XSRF-TOKEN':token},body:data});
  if(!response.ok){let message='Could not upload photos';try{message=(await response.json()).message||message}catch{}throw new Error(message)}
  return response.json();
}

function openRescheduleForm(enquiryId,buyerResponse=false) {
  const today=new Date(),minDate=`${today.getFullYear()}-${String(today.getMonth()+1).padStart(2,'0')}-${String(today.getDate()).padStart(2,'0')}`;
  openModal(`<form class="contact-form" id="rescheduleVisitForm"><div class="eyebrow"><span class="eyebrow-line"></span> UPDATE VISIT DATE</div><h2 id="modalTitle">Choose a date that works.</h2><p>${buyerResponse?'Suggest a date that works for you. The owner will confirm it or suggest another.':'The client will be asked to accept this date before the visit is confirmed.'}</p><label class="full">${buyerResponse?'Your suggested date':'Proposed visit date'}<input name="preferredDate" type="date" min="${minDate}" required></label><p class="auth-error hidden" id="rescheduleError"></p><button class="button button-green" type="submit">${buyerResponse?'Suggest date':'Offer date'} <span>↗</span></button><button class="auth-switch" id="rescheduleBack" type="button">Back to visits</button></form>`);
  $('#rescheduleBack').onclick=persistentDashboard;
  $('#rescheduleVisitForm').onsubmit=async event=>{event.preventDefault();const preferredDate=new FormData(event.currentTarget).get('preferredDate');try{await apiWrite(`/api/enquiries/${enquiryId}/${buyerResponse?'buyer-response':'visit'}`,{status:buyerResponse?'CHANGE_REQUESTED':'RESCHEDULED',preferredDate},'PUT');toast(`Date proposed: ${preferredDate}`);persistentDashboard()}catch(error){const box=$('#rescheduleError');box.textContent=error.message;box.classList.remove('hidden')}};
}

function openPhotoForm(plotId) {
  const plot=plots.find(p=>p.id===plotId);if(!plot)return;
  openModal(`<form class="contact-form" id="photoForm"><div class="eyebrow"><span class="eyebrow-line"></span> PLOT PHOTOS</div><h2 id="modalTitle">Add land photos.</h2><p>Upload up to 8 JPG, PNG, or WebP images at a time. Maximum 8 MB per photo.</p><label class="full">Choose photos<input name="photos" type="file" accept="image/jpeg,image/png,image/webp" multiple required></label><p class="auth-error hidden" id="photoError"></p><button class="button button-green" type="submit">Upload photos <span>↗</span></button></form>`);
  $('#photoForm').onsubmit=async event=>{event.preventDefault();try{const files=event.currentTarget.elements.photos.files;await uploadPhotos(plotId,files);toast('Photos uploaded');openDetail(plotId)}catch(e){const error=$('#photoError');error.textContent=e.message;error.classList.remove('hidden')}};
}

function openPlotForm(editing=null) {
  const val=key=>escapeHtml(editing?.[key]??'');
  openModal(`<form class="contact-form" id="plotForm">
    <div class="eyebrow"><span class="eyebrow-line"></span> ${editing?'UPDATE YOUR LISTING':'ADD A PROPERTY'}</div>
    <h2 id="modalTitle">${editing?'Edit your land listing.':'List your land.'}</h2>
    <p>Add the details buyers need to learn about your land.</p>
    <div class="form-grid">
      <label class="full">Listing title<input name="title" required maxlength="140" placeholder="A short description of the plot" value="${val('title')}"></label>
      <label>City<input name="city" required maxlength="100" value="${val('city')}"></label><label>District<input name="district" required maxlength="100" value="${val('district')}"></label>
      <label>Locality<input name="locality" required maxlength="120" value="${val('locality')}"></label><label>Land type<select name="type">${['Residential','Agricultural','Commercial','Industrial'].map(type=>`<option ${editing?.type===type?'selected':''}>${type}</option>`).join('')}</select></label>
      <label class="full">Address<input name="address" required maxlength="240" value="${val('address')}"></label>
      <label>Price (₹)<input name="price" type="number" min="1" required value="${val('price')}"></label><label>Size (cents)<input name="cents" type="number" min="1" step=".1" required value="${val('cents')}"></label>
      <label>Latitude<input name="latitude" type="number" min="8" max="14" step=".000001" required value="${val('latitude')}"></label><label>Longitude<input name="longitude" type="number" min="76" max="81" step=".000001" required value="${val('longitude')}"></label>
      <label>Road width<input name="roadWidth" required placeholder="30 ft" value="${val('roadWidth')}"></label><label>Facing<input name="facing" required placeholder="East" value="${val('facing')}"></label>
      <label class="full">Description<textarea name="description" required maxlength="1000">${val('description')}</textarea></label>
      <label class="full">Approval / zoning<input name="approvalLabel" placeholder="DTCP approved" value="${val('approvalLabel')}"></label>
      <label class="full">Plot photos (JPG, PNG, WebP; up to 8 MB each)<input name="photos" type="file" accept="image/jpeg,image/png,image/webp" multiple></label>
    </div>
    <p class="auth-error hidden" id="plotError"></p><button class="button button-green" type="submit">${editing?'Save changes':'Save land listing'} <span>↗</span></button>
  </form>`);
  $('#plotForm').onsubmit = async event => {
    event.preventDefault();
    const photoFiles=event.currentTarget.elements.photos.files;
    const data = Object.fromEntries(new FormData(event.currentTarget));delete data.photos;
    const cents=Number(data.cents),price=Number(data.price),lat=Number(data.latitude),lng=Number(data.longitude);
    const id=editing?.id||`TN-${data.city.slice(0,2).toUpperCase()}-${Date.now().toString().slice(-5)}`;
    const offset=Math.max(.0003,Math.min(.002,Math.sqrt(cents)*.0004));
    const plot={...(editing||{}),id,title:data.title,type:data.type,city:data.city,district:data.district,locality:data.locality,address:data.address,latitude:lat,longitude:lng,cents,acres:cents/100,price,pricePerCent:Math.round(price/cents),roadWidth:data.roadWidth,facing:data.facing,zoning:data.type,approved:!!data.approvalLabel,approvalLabel:data.approvalLabel||'Not specified',patta:editing?.patta||'Not specified',surveyNo:editing?.surveyNo||'Not specified',electricity:editing?.electricity||false,water:editing?.water||false,fenced:editing?.fenced||false,owner:mannAccount.fullName,ownerRole:'Owner',phone:mannAccount.phone||'',description:data.description,image:editing?.image||DEMO_PLOTS[0].image,highlights:editing?.highlights||['Owner listed','Location pin'],boundary:[[lat-offset,lng-offset],[lat-offset,lng+offset],[lat+offset,lng+offset],[lat+offset,lng-offset]]};
    try {
      await apiWrite(editing?`/api/plots/${encodeURIComponent(id)}`:'/api/plots',plot,editing?'PUT':'POST');
      if(photoFiles.length) await uploadPhotos(id,photoFiles);
      plots=plots.filter(item=>item.id!==id);applyFilters();persistentDashboard();toast(editing?'Listing updated · sent for review':'Listing submitted for admin review');
    }
    catch(error) { const box=$('#plotError'); box.textContent=error.message; box.classList.remove('hidden'); }
  };
}

async function persistentSubmitEnquiry(event) {
  event.preventDefault();
  const form=event.currentTarget,data=Object.fromEntries(new FormData(form)),button=form.querySelector('[type=submit]');
  button.disabled=true;button.textContent='Sending…';
  try {
    const result=await apiWrite('/api/enquiries',data);
    const isVisit=data.requestType==='SITE_VISIT',plot=plots.find(item=>item.id===data.plotId);
    openModal(`<div class="contact-form success-state"><span class="success-mark">✓</span><div class="eyebrow"><span class="eyebrow-line"></span> REQUEST RECEIVED</div><h2 id="modalTitle">${isVisit?'Your visit request is in.':'Thanks for reaching out.'}</h2><p>Your request #${result.enquiry.id} has been received. ${isVisit?'The property owner can follow up with you to confirm a suitable time.':'The listing contact can follow up with you.'}</p><div class="form-plot"><span class="owner-avatar">${escapeHtml(plot?.owner.split(' ').map(w=>w[0]).slice(0,2).join('')||'MV')}</span><div><b>${escapeHtml(plot?.owner||'Listing contact')}</b><small>${escapeHtml(plot?.locality||'Plot')} · ${escapeHtml(plot?.city||'')}</small></div></div><button class="button button-dark" id="doneBtn">Back to exploring</button></div>`);
    $('#doneBtn').onclick=closeModal;
  } catch(error) { const note=form.querySelector('.form-note');note.textContent=error.message;note.classList.add('auth-error');button.disabled=false;button.textContent=data.requestType==='SITE_VISIT'?'Request a visit':'Send enquiry'; }
}

submitEnquiry = persistentSubmitEnquiry;

async function openDetail(id) {
  const p=plots.find(item=>item.id===id);if(!p)return;selectedPlot=id;renderPins();
  let photos=[];try{const response=await fetch(`/api/plots/${encodeURIComponent(id)}/photos`);if(response.ok)photos=await response.json()}catch{}
  const gallery=[p.image,...photos];
  openModal(`<div class="detail-hero" style="background-image:url('${escapeHtml(gallery[0])}')"><span class="detail-hero-label">${escapeHtml(p.type.toUpperCase())} LAND · ${escapeHtml(p.city.toUpperCase())}</span></div>${gallery.length>1?`<div class="photo-gallery">${gallery.map(url=>`<img src="${escapeHtml(url)}" alt="${escapeHtml(p.title)} photo">`).join('')}</div>`:''}<div class="detail-content"><div class="detail-topline"><div><div class="detail-location">⌖ &nbsp;${escapeHtml(p.address)}</div><h2 id="modalTitle">${escapeHtml(p.title)}</h2></div><div class="detail-price">${money(p.price)}<small>${money(p.pricePerCent)} / cent</small></div></div><div class="detail-facts"><div><small>Plot size</small><b>${escapeHtml(areaLabel(p))} · ${p.cents} cents</b></div><div><small>Road access</small><b>${escapeHtml(p.roadWidth)} road</b></div><div><small>Facing</small><b>${escapeHtml(p.facing)}</b></div><div><small>Approval</small><b>${escapeHtml(p.approvalLabel)}</b></div></div><p class="detail-description">${escapeHtml(p.description)}</p><div class="detail-highlights">${(p.highlights||[]).map(x=>`<span class="highlight">✓ &nbsp;${escapeHtml(x)}</span>`).join('')}</div><div class="detail-section-label">Land details</div><div class="detail-facts"><div><small>Patta</small><b>${escapeHtml(p.patta)}</b></div><div><small>Survey no.</small><b>${escapeHtml(p.surveyNo.replace('Survey ',''))}</b></div><div><small>Electricity</small><b>${p.electricity?'Nearby':'Not listed'}</b></div><div><small>Water</small><b>${p.water?'Available':'Not listed'}</b></div></div><div class="owner-strip"><span class="owner-avatar">${escapeHtml(p.owner.split(' ').map(w=>w[0]).slice(0,2).join(''))}</span><div><b>${escapeHtml(p.owner)}</b><small>${escapeHtml(p.ownerRole)} · Listing contact</small></div><button class="button button-dark" data-profile="${escapeHtml(p.id)}">Owner profile</button></div><div class="detail-buttons"><button class="button button-green" data-prebook="${escapeHtml(p.id)}">Request pre-booking <span>↗</span></button><button class="button button-dark" data-visit="${escapeHtml(p.id)}">Arrange a site visit <span>↗</span></button><button class="button button-dark" data-compare-detail="${escapeHtml(p.id)}">⇆ &nbsp;Compare plot</button></div><small class="prebooking-note">Owner approval is required. No payment is collected in this request.</small></div>`);
  $('#modalContent').querySelector('[data-profile]').onclick=async()=>{try{const response=await fetch(`/api/plots/${encodeURIComponent(id)}/owner`);if(response.ok){showOwnerProfile(await response.json());return}throw new Error('Owner profile details are coming from this listing');}catch{showOwnerProfile({fullName:p.owner,phone:p.phone,email:null,activeListings:plots.filter(item=>item.owner===p.owner).length,listings:plots.filter(item=>item.owner===p.owner)})}};
  $('#modalContent').querySelector('[data-visit]').onclick=()=>openContact(p.id,'SITE_VISIT');
  const prebookButton=$('#modalContent').querySelector('[data-prebook]');
  prebookButton.onclick=()=>openPrebookingForm(p);
  fetch(`/api/prebookings/availability/${encodeURIComponent(p.id)}`).then(response=>response.ok?response.json():null).then(result=>{if(result?.status==='RESERVED'){prebookButton.disabled=true;prebookButton.textContent='Already pre-booked';prebookButton.classList.add('prebooking-disabled')}}).catch(()=>{});
  $('#modalContent').querySelector('[data-compare-detail]').onclick=()=>{if(!compareIds.includes(p.id)){if(compareIds.length===3){toast('Choose up to 3 plots to compare');return}compareIds.push(p.id)}closeModal();applyFilters();toast('Plot added to your comparison')};
  $('#modalContent').querySelector('.detail-location').insertAdjacentHTML('beforeend',`<span class="map-coords">&nbsp;· &nbsp;${p.latitude.toFixed(3)}° N, ${p.longitude.toFixed(3)}° E</span>`);
}

function openPrebookingForm(plot) {
  if(!mannAccount){persistentLogin();return;}
  if(mannAccount.role!=='BUYER'){toast('Sign in with a buyer account to request a pre-booking');return;}
  openModal(`<form class="contact-form" id="prebookingForm"><div class="eyebrow"><span class="eyebrow-line"></span> PRE-BOOK THIS LAND</div><h2 id="modalTitle">Request a pre-booking.</h2><div class="form-plot"><img src="${escapeHtml(plot.image)}" alt=""><div><b>${escapeHtml(plot.title)}</b><small>${escapeHtml(plot.locality)}, ${escapeHtml(plot.city)} · ${money(plot.price)}</small></div></div><p>The owner reviews your request. If approved, the land is held for you. This request does not collect payment.</p><label class="full">Message to the owner <textarea name="message" maxlength="1000" placeholder="Optional: share your intended purchase timeline or a question."></textarea></label><label class="prebooking-ack"><input name="acknowledge" type="checkbox" required> I understand this is a request and the owner must approve it.</label><p class="auth-error hidden" id="prebookingError"></p><button class="button button-green" type="submit">Send pre-booking request <span>↗</span></button><button class="auth-switch" id="prebookingBack" type="button">Back to land details</button></form>`);
  $('#prebookingBack').onclick=()=>openDetail(plot.id);
  $('#prebookingForm').onsubmit=async event=>{
    event.preventDefault();const submit=event.currentTarget.querySelector('[type="submit"]');submit.disabled=true;
    const message=new FormData(event.currentTarget).get('message')?.toString().trim()||null;
    try { await apiWrite('/api/prebookings',{plotId:plot.id,message},'POST');toast('Pre-booking request sent to the owner');persistentDashboard(); }
    catch(error){const box=$('#prebookingError');box.textContent=error.message;box.classList.remove('hidden');submit.disabled=false;}
  };
}

function showOwnerProfile(profile) {
  openModal(`<div class="compare-modal"><div class="eyebrow"><span class="eyebrow-line"></span> LAND OWNER</div><h2 id="modalTitle">${escapeHtml(profile.fullName)}</h2><p>${escapeHtml(profile.phone||'Contact through a listing enquiry')} · ${profile.activeListings} active approved listing${profile.activeListings===1?'':'s'}</p><div class="dashboard-list">${profile.listings.map(plot=>`<button class="dashboard-item" data-owner-plot="${escapeHtml(plot.id)}"><img src="${escapeHtml(plot.image)}" alt=""><span><b>${escapeHtml(plot.title)}</b><small>${escapeHtml(plot.locality)}, ${escapeHtml(plot.city)} · ${money(plot.price)}</small></span><i>↗</i></button>`).join('')||'<p>No active approved plots listed.</p>'}</div></div>`);
  $('#modalContent').querySelectorAll('[data-owner-plot]').forEach(button=>button.onclick=()=>openDetail(button.dataset.ownerPlot));
}

function openSupportForm() {
  const email=mannAccount?.email||'';
  openModal(`<form class="contact-form" id="supportForm"><div class="eyebrow"><span class="eyebrow-line"></span> WE’RE HERE TO HELP</div><h2 id="modalTitle">Send us a query.</h2><p>Tell us about an issue or question. Our team will get back to you.</p><div class="form-grid"><label>Your name<input name="name" required maxlength="120" value="${escapeHtml(mannAccount?.fullName||'')}"></label><label>Your email<input name="email" type="email" required maxlength="255" value="${escapeHtml(email)}"></label><label class="full">What do you need help with?<select name="topic" required><option value="">Choose a topic</option><option>Website issue</option><option>Account or sign-in</option><option>Listing or plot details</option><option>Enquiry or site visit</option><option>Other question</option></select></label><label class="full">Your message<textarea name="message" required minlength="10" maxlength="3000" placeholder="Describe what happened or what you need…"></textarea></label><label class="support-honeypot" aria-hidden="true">Leave this field empty<input name="website" tabindex="-1" autocomplete="off"></label></div><p class="auth-error hidden" id="supportError"></p><button class="button button-green" type="submit">Send query <span>↗</span></button></form>`);
  $('#supportForm').onsubmit=async event=>{
    event.preventDefault();const form=event.currentTarget,button=form.querySelector('[type=submit]'),data=Object.fromEntries(new FormData(form));
    button.disabled=true;button.textContent='Sending…';
    try {
      const result=await apiWrite('/api/support',data);
      openModal(`<div class="contact-form success-state"><span class="success-mark">✓</span><div class="eyebrow"><span class="eyebrow-line"></span> QUERY RECEIVED</div><h2 id="modalTitle">Thanks for letting us know.</h2><p>Your message has been received. Our team will review it and get back to you.</p><button class="button button-dark" id="supportDone">Done</button></div>`);$('#supportDone').onclick=closeModal;
    } catch(error) { const box=$('#supportError');box.textContent=error.message;box.classList.remove('hidden');button.disabled=false;button.textContent='Send query ↗'; }
  };
}

bind();
$('#supportLink').onclick=openSupportForm;$('#footerSupportLink').onclick=openSupportForm;
$('#loginBtn').onclick=persistentLogin;
$('#listPlotBtn').onclick=()=>mannAccount?.role==='OWNER'?persistentDashboard():persistentLogin();
$('#listingList').addEventListener('click',async event=>{
  const button=event.target.closest('[data-save]');
  if(!button)return;
  const id=button.dataset.save;
  if(!mannAccount){toast('Log in to save this plot to your account');return;}
  try{await apiWrite(`/api/saved-plots/${encodeURIComponent(id)}`,undefined,savedIds.has(id)?'POST':'DELETE');}
  catch(error){toast(error.message);}
});
refreshMannAccount();


/* MannVeedu — effects layer. Loaded after app.js/auth.js. Delete this file + effects.css to revert. */
(() => {
  'use strict';
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const fine = matchMedia('(hover: hover) and (pointer: fine)').matches;
  const $ = s => document.querySelector(s);
  const $$ = s => [...document.querySelectorAll(s)];
  const esc = v => (typeof escapeHtml === 'function' ? escapeHtml(v) : String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])));

  /* ---------- Scroll progress bar ---------- */
  const bar = document.createElement('div');
  bar.className = 'fx-progress';
  document.body.appendChild(bar);
  const onScroll = () => {
    const h = document.documentElement.scrollHeight - innerHeight;
    bar.style.transform = `scaleX(${h > 0 ? Math.min(1, scrollY / h) : 0})`;
  };
  addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  /* ---------- Hero: blobs, floating chips, 3D tilt ---------- */
  const hero = $('.hero');
  if (hero) {
    ['b1', 'b2', 'b3'].forEach(c => {
      const b = document.createElement('span');
      b.className = 'fx-blob ' + c;
      hero.appendChild(b);
    });
  }
  const art = $('.hero-art');
  if (art) {
    [['c1', 'Approved layouts'], ['c2', 'Coast to countryside'], ['c3', 'Price per cent, upfront']].forEach(([c, t]) => {
      const chip = document.createElement('span');
      chip.className = 'fx-chip ' + c;
      chip.innerHTML = `<i></i>${t}`;
      art.appendChild(chip);
    });
    if (fine && !reduce) {
      art.addEventListener('pointermove', e => {
        const r = art.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
        art.style.setProperty('--ry', (x * 12).toFixed(2) + 'deg');
        art.style.setProperty('--rx', (-y * 10).toFixed(2) + 'deg');
        art.classList.add('tilting');
      });
      art.addEventListener('pointerleave', () => {
        art.style.setProperty('--ry', '0deg');
        art.style.setProperty('--rx', '0deg');
        setTimeout(() => art.classList.remove('tilting'), 300);
      });
    }
  }

  /* ---------- Scroll reveals ---------- */
  const groups = [
    ['.search-shell'], ['.section-heading'], ['.results-panel'], ['.map-panel'],
    ['.trust-strip > div', true], ['.footer']
  ];
  const io = 'IntersectionObserver' in window ? new IntersectionObserver(entries => {
    entries.forEach(en => { if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); } });
  }, { threshold: .12, rootMargin: '0px 0px -6% 0px' }) : null;
  groups.forEach(([sel, stagger]) => $$(sel).forEach((el, i) => {
    if (!io) return;
    el.classList.add('fx-reveal');
    if (stagger) el.style.setProperty('--d', (i * .12) + 's');
    io.observe(el);
  }));
  $('.map-panel')?.style.setProperty('--d', '.15s');

  /* ---------- Listing cards: staggered entrance, tilt, marker link ---------- */
  const list = $('#listingList');
  let lastSig = '';
  if (list) {
    new MutationObserver(() => {
      const cards = $$('#listingList .listing-card');
      const sig = cards.map(c => c.dataset.id).join('|');
      if (sig === lastSig) return;          // e.g. compare-checkbox re-render: don't replay entrance
      lastSig = sig;
      cards.forEach((c, i) => { c.style.setProperty('--i', Math.min(i, 8)); c.classList.add('fx-new'); });
    }).observe(list, { childList: true });

    if (fine && !reduce) {
      list.addEventListener('pointermove', e => {
        if (e.pointerType !== 'mouse') return;
        const card = e.target.closest('.listing-card');
        if (!card) return;
        const r = card.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
        card.style.setProperty('--ry', (x * 6).toFixed(2) + 'deg');
        card.style.setProperty('--rx', (-y * 5).toFixed(2) + 'deg');
        card.classList.add('tilting');
      });
      list.addEventListener('pointerout', e => {
        const card = e.target.closest('.listing-card');
        if (card && !card.contains(e.relatedTarget)) card.classList.remove('tilting');
      });
    }

    // Hovering a card highlights its price marker on the map.
    const setHot = title => {
      $$('.leaflet-marker-icon').forEach(m => m.classList.toggle('fx-hot', !!title && m.getAttribute('title') === title));
    };
    list.addEventListener('pointerover', e => {
      const card = e.target.closest('.listing-card');
      if (card) setHot(card.querySelector('h3')?.textContent || '');
    });
    list.addEventListener('pointerleave', () => setHot(''));
  }

  /* ---------- Map swoop: after closing a plot's detail view, fly to it ---------- */
  let viewedId = null;
  const backdrop = $('#modalBackdrop');
  if (backdrop) {
    new MutationObserver(() => {
      if (!backdrop.classList.contains('hidden') || !viewedId) return;
      const id = viewedId; viewedId = null;
      try {
        if (typeof realMap === 'undefined' || !realMap || typeof plots === 'undefined') return;
        const p = plots.find(x => x.id === id), panel = $('.map-panel');
        if (!p || !panel) return;
        const r = panel.getBoundingClientRect();
        if (r.bottom < 0 || r.top > innerHeight) return;
        realMap.flyTo([p.latitude, p.longitude], 11, { duration: reduce ? 0 : 1.5 });
      } catch {}
    }).observe(backdrop, { attributes: true, attributeFilter: ['class'] });
  }

  /* ---------- 3D plot viewer (Three.js, lazy-loaded) ---------- */
  let threePromise = null;
  const loadThree = () => threePromise || (threePromise = new Promise((res, rej) => {
    if (window.THREE) return res(window.THREE);
    const s = document.createElement('script');
    s.src = 'https://unpkg.com/three@0.128.0/build/three.min.js';
    s.onload = () => res(window.THREE);
    s.onerror = () => { threePromise = null; rej(new Error('three')); };
    document.head.appendChild(s);
  }));

  let viewer = null;
  const disposeViewer = () => {
    if (!viewer) return;
    cancelAnimationFrame(viewer.raf);
    removeEventListener('resize', viewer.onResize);
    try {
      viewer.scene.traverse(o => {
        o.geometry?.dispose();
        [].concat(o.material || []).forEach(m => { m.map?.dispose(); m.dispose(); });
      });
      viewer.renderer.dispose();
    } catch {}
    viewer = null;
  };

  function labelSprite(THREE, text, color = '#2f4b36') {
    const c = document.createElement('canvas'); c.width = 128; c.height = 64;
    const g = c.getContext('2d');
    g.font = '700 38px DM Sans, sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillStyle = color; g.fillText(text, 64, 34);
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(c), transparent: true, depthTest: false }));
    sp.scale.set(1.6, .8, 1); sp.renderOrder = 10;
    return sp;
  }

  function buildScene(THREE, p, canvas) {
    const W = canvas.clientWidth || 600, H = 260;
    const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
    renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
    renderer.setSize(W, H, false);
    renderer.shadowMap.enabled = true;
    const scene = new THREE.Scene();
    scene.fog = new THREE.Fog(0xeef1e4, 22, 40);
    const camera = new THREE.PerspectiveCamera(38, W / H, .1, 100);

    scene.add(new THREE.HemisphereLight(0xffffff, 0xb9c9a6, .85));
    const sun = new THREE.DirectionalLight(0xfff3d6, .9);
    sun.position.set(7, 12, 5); sun.castShadow = true;
    sun.shadow.mapSize.set(1024, 1024);
    Object.assign(sun.shadow.camera, { left: -14, right: 14, top: 14, bottom: -14 });
    scene.add(sun);

    // Illustrative geometry: real area, assumed 1 : 1.4 proportions.
    const area = Math.max(1, (Number(p.cents) || 1) * 435.6);
    const wFt = Math.sqrt(area * 1.4), dFt = area / wFt;
    const s = 10 / Math.max(wFt, dFt), sw = wFt * s, sd = dFt * s;
    const face = String(p.facing || '').toLowerCase();
    const side = face.includes('east') ? [1, 0] : face.includes('west') ? [-1, 0] : face.includes('south') ? [0, 1] : [0, -1];
    const roadFt = parseFloat(p.roadWidth) || 24;
    const rw = Math.max(.5, Math.min(3, roadFt * s));

    const ground = new THREE.Mesh(new THREE.CircleGeometry(15, 64), new THREE.MeshStandardMaterial({ color: 0xcfe0bd, roughness: 1 }));
    ground.rotation.x = -Math.PI / 2; ground.receiveShadow = true; scene.add(ground);

    const plotGroup = new THREE.Group(); scene.add(plotGroup);
    const slab = new THREE.Mesh(new THREE.BoxGeometry(sw, .2, sd), new THREE.MeshStandardMaterial({ color: p.type === 'Agricultural' ? 0x9bb872 : 0xe8d9a8, roughness: .95 }));
    slab.position.y = .1; slab.receiveShadow = true; slab.castShadow = true; plotGroup.add(slab);
    const edge = new THREE.LineSegments(new THREE.EdgesGeometry(slab.geometry), new THREE.LineBasicMaterial({ color: 0xc66e4f }));
    edge.position.copy(slab.position); plotGroup.add(edge);

    // Facing arrow
    const arrow = new THREE.Mesh(new THREE.ConeGeometry(.32, .95, 20), new THREE.MeshStandardMaterial({ color: 0xc66e4f }));
    arrow.position.set(0, .45, 0);
    if (side[0] === 1) arrow.rotation.z = -Math.PI / 2; else if (side[0] === -1) arrow.rotation.z = Math.PI / 2;
    else if (side[1] === -1) arrow.rotation.x = -Math.PI / 2; else arrow.rotation.x = Math.PI / 2;
    arrow.castShadow = true; plotGroup.add(arrow);

    // Road on the facing side
    const alongX = side[0] === 0;
    const roadLen = (alongX ? sw : sd) + 8;
    const road = new THREE.Mesh(new THREE.BoxGeometry(alongX ? roadLen : rw, .06, alongX ? rw : roadLen), new THREE.MeshStandardMaterial({ color: 0x5f645d, roughness: 1 }));
    const off = (alongX ? sd : sw) / 2 + rw / 2 + .15;
    road.position.set(side[0] * off, .03, side[1] * off); road.receiveShadow = true; scene.add(road);
    const line = new THREE.Mesh(new THREE.BoxGeometry(alongX ? roadLen : .07, .07, alongX ? .07 : roadLen), new THREE.MeshBasicMaterial({ color: 0xf3efe0 }));
    line.position.set(road.position.x, .045, road.position.z); scene.add(line);

    // Fence posts
    if (p.fenced) {
      const postMat = new THREE.MeshStandardMaterial({ color: 0x7a5c3e });
      const step = Math.max(.9, Math.max(sw, sd) / 12);
      const post = (x, z) => { const m = new THREE.Mesh(new THREE.BoxGeometry(.09, .5, .09), postMat); m.position.set(x, .45, z); m.castShadow = true; plotGroup.add(m); };
      for (let x = -sw / 2; x <= sw / 2 + .01; x += step) { post(x, -sd / 2); post(x, sd / 2); }
      for (let z = -sd / 2 + step; z < sd / 2 - .01; z += step) { post(-sw / 2, z); post(sw / 2, z); }
    }
    // Power pole + water patch
    if (p.electricity) {
      const pole = new THREE.Mesh(new THREE.CylinderGeometry(.06, .08, 2.2, 8), new THREE.MeshStandardMaterial({ color: 0x6b5a45 }));
      const far = off + rw / 2 + .4;
      if (side[0]) pole.position.set(side[0] * far, 1.1, sd / 2); else pole.position.set(sw / 2, 1.1, side[1] * far);
      pole.castShadow = true; scene.add(pole);
    }
    if (p.water) {
      const w = new THREE.Mesh(new THREE.CircleGeometry(.55, 28), new THREE.MeshStandardMaterial({ color: 0x6fb0d6, roughness: .2 }));
      w.rotation.x = -Math.PI / 2; w.position.set(-sw / 4, .215, -sd / 4); plotGroup.add(w);
    }

    // Trees (deterministic, away from the road)
    const trunk = new THREE.MeshStandardMaterial({ color: 0x7a5c3e }), leaf = new THREE.MeshStandardMaterial({ color: 0x4f8a55 }), leaf2 = new THREE.MeshStandardMaterial({ color: 0x6aa35c });
    const R = Math.max(sw, sd) / 2 + 3.2;
    for (let i = 0; i < 12; i++) {
      const a = i / 12 * Math.PI * 2 + .2, cx = Math.cos(a), cz = Math.sin(a);
      if (cx * side[0] + cz * side[1] > .45) continue;
      const r = R + (i % 3) * .8, g = new THREE.Group(), h = .9 + (i % 4) * .22;
      const t = new THREE.Mesh(new THREE.CylinderGeometry(.07, .1, .5, 6), trunk); t.position.y = .25;
      const c = new THREE.Mesh(new THREE.ConeGeometry(.5, h * 1.5, 8), i % 2 ? leaf : leaf2); c.position.y = .5 + h * .6;
      t.castShadow = c.castShadow = true; g.add(t, c); g.position.set(cx * r, 0, cz * r); scene.add(g);
    }

    // Compass
    const comp = new THREE.Group(); comp.position.set(-sw / 2 - 2.4, .05, sd / 2 + 2.4);
    if (Math.abs(comp.position.x) > 9) comp.position.x = -9; if (comp.position.z > 9) comp.position.z = 9;
    const ring = new THREE.Mesh(new THREE.RingGeometry(.8, .9, 40), new THREE.MeshBasicMaterial({ color: 0x2f4b36, side: THREE.DoubleSide }));
    ring.rotation.x = -Math.PI / 2; comp.add(ring);
    const north = new THREE.Mesh(new THREE.ConeGeometry(.22, .8, 4), new THREE.MeshBasicMaterial({ color: 0xc0412a }));
    north.rotation.x = -Math.PI / 2; north.position.set(0, .05, -.5); comp.add(north);
    const nl = labelSprite(THREE, 'N', '#c0412a'); nl.position.set(0, .4, -1.45); comp.add(nl);
    scene.add(comp);

    // Rise-in animation + orbit controls
    plotGroup.scale.y = .01;
    const t0 = performance.now();
    const st = { az: .6, el: .72, r: 17, auto: !reduce, drag: null };
    const pos = () => camera.position.set(
      Math.sin(st.az) * Math.cos(st.el) * st.r, Math.sin(st.el) * st.r, Math.cos(st.az) * Math.cos(st.el) * st.r);
    canvas.addEventListener('pointerdown', e => { st.drag = { x: e.clientX, y: e.clientY }; st.auto = false; canvas.setPointerCapture(e.pointerId); });
    canvas.addEventListener('pointermove', e => {
      if (!st.drag) return;
      st.az -= (e.clientX - st.drag.x) * .008;
      st.el = Math.max(.25, Math.min(1.35, st.el + (e.clientY - st.drag.y) * .006));
      st.drag = { x: e.clientX, y: e.clientY };
    });
    const end = () => { st.drag = null; };
    canvas.addEventListener('pointerup', end); canvas.addEventListener('pointercancel', end);

    const v = { scene, renderer, raf: 0, dims: { wFt, dFt }, roadFt };
    v.onResize = () => {
      const w = canvas.clientWidth; if (!w) return;
      renderer.setSize(w, H, false); camera.aspect = w / H; camera.updateProjectionMatrix();
    };
    addEventListener('resize', v.onResize);
    const loop = now => {
      const k = Math.min(1, (now - t0) / 1000), e = 1 - Math.pow(1 - k, 3);
      plotGroup.scale.y = reduce ? 1 : Math.max(.01, e);
      if (st.auto) st.az += .004;
      pos(); camera.lookAt(0, .3, 0);
      renderer.render(scene, camera);
      v.raf = requestAnimationFrame(loop);
    };
    v.raf = requestAnimationFrame(loop);
    return v;
  }

  async function mount3D(content) {
    if (content.querySelector('.fx-3d')) return;
    const anchor = content.querySelector('.detail-section-label') || content.querySelector('.owner-strip');
    if (!anchor || typeof plots === 'undefined' || typeof selectedPlot === 'undefined') return;
    const p = plots.find(x => x.id === selectedPlot);
    if (!p) return;
    viewedId = p.id;
    const box = document.createElement('div');
    box.className = 'fx-3d';
    box.innerHTML = `<div class="fx-3d-head"><span>3D plot view</span><span>Drag to rotate · illustrative layout</span></div><canvas aria-label="3D illustration of the plot, its facing direction and road"></canvas><div class="fx-3d-legend"></div>`;
    anchor.parentNode.insertBefore(box, anchor);
    try {
      const THREE = await loadThree();
      if (!box.isConnected) return;       // modal closed while loading
      disposeViewer();
      viewer = buildScene(THREE, p, box.querySelector('canvas'));
      const d = viewer.dims;
      box.querySelector('.fx-3d-legend').innerHTML =
        `<span><b>${esc(p.cents)} cents</b> ≈ ${Math.round(d.wFt)} × ${Math.round(d.dFt)} ft (assumed shape)</span><span>Facing <b>${esc(p.facing)}</b></span><span><b>${esc(p.roadWidth)}</b> road</span>${p.fenced ? '<span>Fenced</span>' : ''}${p.electricity ? '<span>Power nearby</span>' : ''}${p.water ? '<span>Water</span>' : ''}`;
    } catch {
      box.innerHTML = '<div class="fx-3d-fallback">The 3D view could not load right now. The plot details below are unaffected.</div>';
    }
  }

  const mc = $('#modalContent');
  if (mc) {
    new MutationObserver(() => {
      if (viewer && !viewer.scene) disposeViewer();
      const canvas = mc.querySelector('.fx-3d canvas');
      if (viewer && !canvas) disposeViewer();                 // modal changed or closed
      const dc = mc.querySelector('.detail-content');
      if (dc && !dc.querySelector('.fx-3d')) mount3D(dc);
    }).observe(mc, { childList: true });
  }
})();