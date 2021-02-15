package de.cotech.hw.database.sample.ui;


import android.os.Bundle;
import android.widget.Toast;

import net.sqlcipher.database.SQLiteConstraintException;

import java.util.List;

import de.cotech.hw.database.sample.R;
import de.cotech.hw.database.sample.db.EncryptedDatabase;
import de.cotech.hw.database.sample.db.entity.User;


public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.buttonInsert).setOnClickListener(v -> insert());
        findViewById(R.id.buttonQuery).setOnClickListener(v -> query());
    }

    private void insert() {
        // TODO: use your favorite way of threading in your app
        new Thread(() -> {
            User testUser = new User();
            testUser.firstName = "Martin";
            testUser.lastName = "Sonneborn";
            try {
                EncryptedDatabase.getInstance().userDao().insertAll(testUser);
            } catch (SQLiteConstraintException e) {
                MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this, "user already inserted", Toast.LENGTH_LONG).show());
                return;
            }

            MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this, "users successfully inserted", Toast.LENGTH_LONG).show());
        }).start();
    }

    private void query() {
        // TODO: use your favorite way of threading in your app
        new Thread(() -> {
            List<User> users = EncryptedDatabase.getInstance().userDao().getAll();

            MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this, "users: " + users.toString(), Toast.LENGTH_LONG).show());
        }).start();
    }
}
