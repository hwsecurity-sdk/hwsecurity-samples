package de.cotech.hw.database.sample.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class User {
    @PrimaryKey
    public int uid;

    @ColumnInfo(name = "first_name")
    public String firstName;

    @ColumnInfo(name = "last_name")
    public String lastName;

    @NonNull
    @Override
    public String toString() {
        return "uid=" + uid + "\nfirst_name=" + firstName + "\nlast_name=" + lastName;
    }
}
