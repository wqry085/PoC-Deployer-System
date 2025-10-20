package com.wqry085.deployesystem;

public class PluginItem {
    public String name;
    public String version;
    public String description;
    public String downloadUrl;

    public PluginItem(String name, String version, String description, String downloadUrl) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.downloadUrl = downloadUrl;
    }
}