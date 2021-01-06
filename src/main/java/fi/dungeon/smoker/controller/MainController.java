package fi.dungeon.smoker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {
	
	// Serves Vue files
	
    @GetMapping("/")
    public String index(Model model) {
        // model.addAttribute("", "");
        return "index";
    }
}