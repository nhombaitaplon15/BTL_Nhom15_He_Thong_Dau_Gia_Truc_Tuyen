package com.auction.common.model;

public abstract class Entity {
    protected int id;
    public Entity(int id) {
        this.id = id;
    }

  public Entity() {

  }

  public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
}
