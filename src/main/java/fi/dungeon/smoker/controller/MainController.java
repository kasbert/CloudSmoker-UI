package fi.dungeon.smoker.controller;

import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class MainController implements ErrorController {
    private static final String PATH = "/error";
    Logger logger = LoggerFactory.getLogger(MainController.class);

	// Serves Vue files
	
    @GetMapping("/")
    public String index(Model model) {
        // model.addAttribute("", "");
        return "index";
    }

    @RequestMapping(value = PATH)
    public String error(HttpServletRequest request, Model model) {
        logger.debug("Error " + request);
        String message = (String) request.getSession().getAttribute("error.message");
        logger.debug("Error " + message);
        model.addAttribute("error", "error");
        return "forward:/";
    }

    @Override
    public String getErrorPath() {
        return PATH;
    }
}