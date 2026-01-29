/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

/**
 *
 * @author ntu-user
 */
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Configuration {

    private Configuration() {
        // prevent instantiation
    }

    public static final Path STORAGE_LOCAL_DIR =
            Paths.get("/home/ntu-user/NetBeansProjects/comp20081-cwk/storage_local");
    
    public static final boolean SIMULATE_DELAY =
        Boolean.parseBoolean(System.getenv().getOrDefault("SIM_DELAY", "false")); //artificial delay affects
    
    public static final int DELAY_MIN_MS =
        Integer.parseInt(System.getenv().getOrDefault("DELAY_MIN_MS", "3000")); //3 to
    public static final int DELAY_MAX_MS =
        Integer.parseInt(System.getenv().getOrDefault("DELAY_MAX_MS", "9000"));//9 second delay
}
