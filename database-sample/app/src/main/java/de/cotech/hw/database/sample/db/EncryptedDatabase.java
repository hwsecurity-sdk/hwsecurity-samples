package de.cotech.hw.database.sample.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.commonsware.cwac.saferoom.SafeHelperFactory;

import de.cotech.hw.database.sample.db.dao.UserDao;
import de.cotech.hw.database.sample.db.entity.User;
import de.cotech.hw.secrets.ByteSecret;

@Database(entities = {User.class}, version = 1)
public abstract class EncryptedDatabase extends RoomDatabase {

    private static EncryptedDatabase sInstance;

    @VisibleForTesting
    public static final String DATABASE_NAME = "encrypted-sample-db";

    public static EncryptedDatabase decryptAndGetInstance(final Context context, ByteSecret secret) {
        if (sInstance == null) {
            synchronized (EncryptedDatabase.class) {
                if (sInstance == null) {
                    sInstance = buildDatabase(context.getApplicationContext(), secret);
                }
            }
        }
        return sInstance;
    }

    public static EncryptedDatabase getInstance() {
        if (sInstance == null) {
            return null;
        } else {
            return sInstance;
        }
    }

    private static EncryptedDatabase buildDatabase(final Context appContext, ByteSecret secret) {
        SafeHelperFactory factory = new SafeHelperFactory(secret.getByteCopyAndClear());

        return Room.databaseBuilder(appContext, EncryptedDatabase.class, DATABASE_NAME)
                .openHelperFactory(factory)
                .addCallback(new Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                        // TODO: populate database with initial data
                    }
                })
                .build();
    }

    public abstract UserDao userDao();

}
