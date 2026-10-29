package in.tamiland.market;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/saved-plots")
public class SavedPlotController {
    private final JdbcTemplate jdbc; private final PlotService plots;
    public SavedPlotController(JdbcTemplate jdbc,PlotService plots){this.jdbc=jdbc;this.plots=plots;}
    @GetMapping public List<Plot> list(Authentication user){return jdbc.query("SELECT plot_id FROM saved_plots WHERE user_email=? ORDER BY saved_at DESC",(rs,n)->rs.getString(1),user.getName()).stream().map(id->plots.find(id).orElse(null)).filter(p->p!=null).toList();}
    @PostMapping("/{plotId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@PathVariable String plotId,Authentication user){if(plots.find(plotId).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plot not found");jdbc.update("INSERT INTO saved_plots(user_email,plot_id) VALUES(?,?) ON CONFLICT (user_email,plot_id) DO NOTHING",user.getName(),plotId);}
    @DeleteMapping("/{plotId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String plotId,Authentication user){jdbc.update("DELETE FROM saved_plots WHERE user_email=? AND plot_id=?",user.getName(),plotId);}
}
