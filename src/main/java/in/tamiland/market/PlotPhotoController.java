package in.tamiland.market;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/plots/{id}/photos")
public class PlotPhotoController {
    private static final int MAX_PHOTOS_PER_PLOT=20;
    private final PlotService plots;
    private final Path uploadRoot;

    public PlotPhotoController(PlotService plots,@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.plots=plots;this.uploadRoot=Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostMapping
    public List<String> upload(@PathVariable String id,@RequestParam("files") List<MultipartFile> files,Authentication user) {
        if(!plots.ownsPlot(id,user.getName())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Your active plot was not found");
        if(files.isEmpty()||files.size()>8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Upload between 1 and 8 photos at a time");
        if(plots.photoCount(id)+files.size()>MAX_PHOTOS_PER_PLOT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A listing can have at most "+MAX_PHOTOS_PER_PLOT+" photos");
        try {
            Path folder=uploadRoot.resolve(id).normalize();
            if(!folder.startsWith(uploadRoot)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid plot ID");
            Files.createDirectories(folder);
            for(MultipartFile file:files) {
                String contentType=file.getContentType();
                String ext=switch(contentType==null?"":contentType.toLowerCase()) {
                    case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp";
                    default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only JPG, PNG, and WebP images are accepted");
                };
                if(file.isEmpty()||file.getSize()>8*1024*1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Each photo must be between 1 byte and 8 MB");
                if(!matchesImageSignature(file,ext)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"File contents do not match a JPG, PNG, or WebP image");
                String name=UUID.randomUUID()+ext;
                file.transferTo(folder.resolve(name));
                plots.addPhoto(id,"/uploads/"+id+"/"+name);
            }
            return plots.photos(id);
        } catch(ResponseStatusException e) { throw e; }
        catch(Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Could not save plot photos",e); }
    }

    /** The declared Content-Type is client-controlled, so also check the file's leading bytes. */
    static boolean matchesImageSignature(MultipartFile file,String ext) {
        try(var in=file.getInputStream()) {
            byte[] h=in.readNBytes(12);
            return switch(ext) {
                case ".jpg" -> h.length>=3&&(h[0]&0xFF)==0xFF&&(h[1]&0xFF)==0xD8&&(h[2]&0xFF)==0xFF;
                case ".png" -> h.length>=8&&(h[0]&0xFF)==0x89&&h[1]=='P'&&h[2]=='N'&&h[3]=='G'&&h[4]==0x0D&&h[5]==0x0A&&h[6]==0x1A&&h[7]==0x0A;
                case ".webp" -> h.length>=12&&h[0]=='R'&&h[1]=='I'&&h[2]=='F'&&h[3]=='F'&&h[8]=='W'&&h[9]=='E'&&h[10]=='B'&&h[11]=='P';
                default -> false;
            };
        } catch(java.io.IOException e) { return false; }
    }
}
