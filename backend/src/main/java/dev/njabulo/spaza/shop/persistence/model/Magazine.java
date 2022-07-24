package dev.njabulo.spaza.shop.persistence.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;

@Entity(name = "MAGAZINE_STAND")
public class Magazine {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Getter
    private long id;

    @Column
    @Getter
    @Setter
    private String name;

    @Column
    @Getter
    @Setter
    private byte[] cover;

    @Column
    @Getter
    @Setter
    private double price;

    @Column
    @Getter
    @Setter
    private int issue;

    @Column
    @Getter
    @Setter
    private String sku;

    @Column
    @Getter
    @Setter
    private String month;

    @Column
    @Getter
    @Setter
    private int year;
}
