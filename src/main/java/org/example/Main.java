package org.example;

public class Main {
    public static void main(String[] args) {
        if (!"opengl".equalsIgnoreCase(System.getProperty("hrassmg.renderer", "java2d"))) {
            System.setProperty("sun.java2d.d3d", "false");
            System.setProperty("sun.java2d.opengl", "false");
        }
        new Game().run();
    }
}
