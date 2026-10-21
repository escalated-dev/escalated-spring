package dev.escalated.ui.controllers;

import dev.escalated.controllers.AttachmentController;
import dev.escalated.ui.UiRoutes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Attachment downloads for the signed-in pages. The API route takes a token, which a browser
 * session does not carry; this one takes the session, with the same access rules.
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class UiAttachmentController {

    private final AttachmentController attachments;

    public UiAttachmentController(AttachmentController attachments) {
        this.attachments = attachments;
    }

    @GetMapping(UiRoutes.PREFIX + "/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, Authentication authentication) {
        return attachments.download(id, authentication);
    }
}
