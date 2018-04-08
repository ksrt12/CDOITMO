package com.bukhmastov.cdoitmo.object.entity;

public class Suggestion {
    public final int icon;
    public final String query;
    public final String title;
    public final boolean removable;
    public Suggestion(String query, String title, int icon, boolean removable){
        this.icon = icon;
        this.query = query;
        this.title = title;
        this.removable = removable;
    }
}