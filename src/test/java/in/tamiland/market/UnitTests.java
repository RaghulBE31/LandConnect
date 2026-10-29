package in.tamiland.market;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class UnitTests {
    static Plot valid() {
        return new Plot("TN-CH-90001","Corner plot near the coast","Residential","Chennai","Chengalpattu","Uthandi","ECR, Chennai",
                12.87,80.24,8,0.2,6_400_000,800_000,"30 ft","East","Residential",true,"CMDA approved","Available","Survey 1",true,true,true,
                "X","Owner","+91 98765 41028","A calm plot.","https://images.example.com/a.jpg?w=1000&q=85",List.of("Corner site"),
                List.of(new Plot.Point(12.871,80.239)));
    }
    static Plot with(java.util.function.UnaryOperator<Plot> f) { return f.apply(valid()); }

    @Test void acceptsValidListing() { assertDoesNotThrow(()->PlotValidator.validate(valid())); }

    @Test void rejectsMarkupInTextFields() {
        Plot p=new Plot("TN-CH-90001","<img src=x onerror=alert(1)>","Residential","Chennai","Chengalpattu","Uthandi","a",12.87,80.24,8,0.2,1,1,"","","",false,"","","",false,false,false,"X","Owner","","d",null,List.of(),List.of(),null);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->PlotValidator.validate(p)).getStatusCode().value());
        Plot q=valid().withContact("X","Owner","1234567");
        Plot desc=new Plot(q.id(),q.title(),q.type(),q.city(),q.district(),q.locality(),q.address(),q.latitude(),q.longitude(),q.cents(),q.acres(),q.price(),q.pricePerCent(),q.roadWidth(),q.facing(),q.zoning(),q.approved(),q.approvalLabel(),q.patta(),q.surveyNo(),q.electricity(),q.water(),q.fenced(),q.owner(),q.ownerRole(),q.phone(),"hello <script>alert(1)</script>",q.image(),q.highlights(),q.boundary(),null);
        assertThrows(ResponseStatusException.class,()->PlotValidator.validate(desc));
    }

    @Test void rejectsBadImageAndLocation() {
        Plot v=valid();
        for(String img:new String[]{"javascript:alert(1)","http://insecure.example/a.jpg","https://x.example/a'onerror='x","https://x.example/a.jpg\") ;"}) {
            Plot p=new Plot(v.id(),v.title(),v.type(),v.city(),v.district(),v.locality(),v.address(),v.latitude(),v.longitude(),v.cents(),v.acres(),v.price(),v.pricePerCent(),v.roadWidth(),v.facing(),v.zoning(),v.approved(),v.approvalLabel(),v.patta(),v.surveyNo(),v.electricity(),v.water(),v.fenced(),v.owner(),v.ownerRole(),v.phone(),v.description(),img,v.highlights(),v.boundary(),null);
            assertThrows(ResponseStatusException.class,()->PlotValidator.validate(p),img);
        }
        Plot far=new Plot(v.id(),v.title(),v.type(),v.city(),v.district(),v.locality(),v.address(),51.5,-0.12,v.cents(),v.acres(),v.price(),v.pricePerCent(),v.roadWidth(),v.facing(),v.zoning(),v.approved(),v.approvalLabel(),v.patta(),v.surveyNo(),v.electricity(),v.water(),v.fenced(),v.owner(),v.ownerRole(),v.phone(),v.description(),v.image(),v.highlights(),v.boundary(),null);
        assertThrows(ResponseStatusException.class,()->PlotValidator.validate(far));
        Plot upload=new Plot(v.id(),v.title(),v.type(),v.city(),v.district(),v.locality(),v.address(),v.latitude(),v.longitude(),v.cents(),v.acres(),v.price(),v.pricePerCent(),v.roadWidth(),v.facing(),v.zoning(),v.approved(),v.approvalLabel(),v.patta(),v.surveyNo(),v.electricity(),v.water(),v.fenced(),v.owner(),v.ownerRole(),v.phone(),v.description(),"/uploads/TN-CH-90001/abc.jpg",v.highlights(),v.boundary(),null);
        assertDoesNotThrow(()->PlotValidator.validate(upload));
    }

    @Test void rejectsNonPositivePriceAndBadId() {
        Plot v=valid();
        Plot free=new Plot(v.id(),v.title(),v.type(),v.city(),v.district(),v.locality(),v.address(),v.latitude(),v.longitude(),v.cents(),v.acres(),0,0,v.roadWidth(),v.facing(),v.zoning(),v.approved(),v.approvalLabel(),v.patta(),v.surveyNo(),v.electricity(),v.water(),v.fenced(),v.owner(),v.ownerRole(),v.phone(),v.description(),v.image(),v.highlights(),v.boundary(),null);
        assertThrows(ResponseStatusException.class,()->PlotValidator.validate(free));
        Plot badId=new Plot("../etc",v.title(),v.type(),v.city(),v.district(),v.locality(),v.address(),v.latitude(),v.longitude(),v.cents(),v.acres(),v.price(),v.pricePerCent(),v.roadWidth(),v.facing(),v.zoning(),v.approved(),v.approvalLabel(),v.patta(),v.surveyNo(),v.electricity(),v.water(),v.fenced(),v.owner(),v.ownerRole(),v.phone(),v.description(),v.image(),v.highlights(),v.boundary(),null);
        assertThrows(ResponseStatusException.class,()->PlotValidator.validate(badId));
    }

    static class FakeClock extends Clock {
        final AtomicLong now=new AtomicLong(1_000_000);
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        public long millis() { return now.get(); }
    }

    @Test void rateLimiterBlocksAfterMaxAndRecovers() {
        FakeClock clock=new FakeClock();
        RateLimitFilter f=new RateLimitFilter(clock);
        var rule=new RateLimitFilter.Rule(3,60_000);
        for(int i=0;i<3;i++) assertTrue(f.allow("login|1.2.3.4",rule));
        assertFalse(f.allow("login|1.2.3.4",rule));
        assertTrue(f.allow("login|5.6.7.8",rule),"other clients are unaffected");
        clock.now.addAndGet(60_001);
        assertTrue(f.allow("login|1.2.3.4",rule),"window expired");
    }

    @Test void imageSignatureChecksRealBytes() {
        byte[] png={(byte)0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A,0,0,0,0};
        byte[] jpg={(byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0,0,0,0,0,0,0,0};
        byte[] webp={'R','I','F','F',0,0,0,0,'W','E','B','P'};
        byte[] html="<script>alert(1)</script>".getBytes();
        assertTrue(PlotPhotoController.matchesImageSignature(new MockMultipartFile("f","a.png","image/png",png),".png"));
        assertTrue(PlotPhotoController.matchesImageSignature(new MockMultipartFile("f","a.jpg","image/jpeg",jpg),".jpg"));
        assertTrue(PlotPhotoController.matchesImageSignature(new MockMultipartFile("f","a.webp","image/webp",webp),".webp"));
        assertFalse(PlotPhotoController.matchesImageSignature(new MockMultipartFile("f","a.png","image/png",html),".png"));
        assertFalse(PlotPhotoController.matchesImageSignature(new MockMultipartFile("f","a.jpg","image/jpeg",png),".jpg"));
    }
}
