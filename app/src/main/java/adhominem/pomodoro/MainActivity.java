package adhominem.pomodoro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import adhominem.pomodoro.DBHelperContract.DBEntry;

import java.io.IOException;

@SuppressLint("SetTextI18n")
public class MainActivity extends Activity implements AdapterView.OnItemSelectedListener {

    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = 60000;
    private static final long POMODORO_DURATION = ONE_MINUTE * 25;
    private static final long SHORT_BREAK_DURATION = ONE_MINUTE * 5;
    private static final long LONG_BREAK_DURATION = ONE_MINUTE * 15;

    private PomodoroTimer timer;
    private Button button;
    private ProgressBar progressBar;
    private TextView caption;
    private RatingBar ratingBar;
    private TextView textView;
    private static final String TAG = "MainActivity";
    private int pomodoros;
    private String session;
    private int sessionCount;
    private TextView pomodoroStatsText;
    private Ringtone alarm;

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        session = String.valueOf(parent.getItemAtPosition(position));
        sessionCount = getCount(session);
        render();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    private enum Phase {POMODORO, SHORT_BREAK, LONG_BREAK};
    private String captionString;
    private String buttonString;
    private DBHelper dbHelper;

    class PomodoroTimer extends CountDownTimer {

        private boolean timerIsRunning;
        private long millisUntilFinished;
        private Phase phase;

        private PomodoroTimer(long millisUntilFinished, Phase phase) {
            super (millisUntilFinished, ONE_SECOND);
            timerIsRunning = false;
            this.millisUntilFinished = millisUntilFinished;
            this.phase = phase;
        }

        long getMillisUntilFinished() {
            return millisUntilFinished;
        }

        boolean isTimerRunning() {
            return timerIsRunning;
        }

        void toggleTimerIsRunning() {
            timerIsRunning = !timerIsRunning;
            button.setText(timerIsRunning ? "Pause" : "Start");
        }

        Phase getPhase() {
            return phase;
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onTick(long millisUntilFinished) {

            // This stores the remaining time. If timer is stopped, new total will be used
            this.millisUntilFinished = millisUntilFinished;
            textView.setText(String.format("%02d : %02d", millisUntilFinished / ONE_MINUTE,
                    millisUntilFinished % ONE_MINUTE / ONE_SECOND));
            progressBar.incrementProgressBy((int) ONE_SECOND);
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onFinish() {
            timerIsRunning = false;

            switchPhase();
            render();

            textView.setText(String.format("%02d : %02d", millisUntilFinished / ONE_MINUTE,
                    millisUntilFinished % ONE_MINUTE / ONE_SECOND));
        }

        void switchPhase() {
            progressBar.setProgress(0);
            if (phase == Phase.POMODORO) {
                alarm.play();
                pomodoros += 1;
                addSession(session);
                ++sessionCount;
                // determine next phase
                // take a break
                if (pomodoros == 4) {
                    phase = Phase.LONG_BREAK;
                    millisUntilFinished = LONG_BREAK_DURATION;
                    progressBar.setMax((int) (LONG_BREAK_DURATION));
                    captionString = "Long break";
                    buttonString = "Start long break";
                } else {
                    phase = Phase.SHORT_BREAK;
                    millisUntilFinished = SHORT_BREAK_DURATION;
                    progressBar.setMax((int) (SHORT_BREAK_DURATION));
                    captionString = "Short break";
                    buttonString = "Start short break";
                }
            } else {
                phase = Phase.POMODORO;
                millisUntilFinished = POMODORO_DURATION;
                progressBar.setMax((int) (POMODORO_DURATION));
                captionString = "Pomodoro";
                buttonString = "Start";
            }
        }
    }

    void render() {
        caption.setText(captionString);
        button.setText(buttonString);
        ratingBar.setRating(pomodoros);
        pomodoroStatsText.setText("Current pomodoros spent on\n" + session + ": " + sessionCount);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        button = (Button) findViewById(R.id.button);
        textView = (TextView) findViewById(R.id.textView);
        ratingBar = (RatingBar) findViewById(R.id.ratingBar);
        caption = (TextView) findViewById(R.id.caption);
        progressBar = (ProgressBar) findViewById(R.id.progressBar3);
        progressBar.setMax((int) POMODORO_DURATION);
        pomodoroStatsText = (TextView) findViewById(R.id.pomodoroStatsText);
        dbHelper = new DBHelper(getApplicationContext());
        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(this);
        pomodoros = 0;
        buttonString = "Start";
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        alarm = RingtoneManager.getRingtone(getApplicationContext(), notification);
    }

    public void toggleTimer(View v) {
        alarm.stop();

        if (timer == null) {
            timer = new PomodoroTimer(POMODORO_DURATION, Phase.POMODORO);
            timer.start();
        } else if (timer.isTimerRunning()) {
            timer.cancel();
        } else {
            // get remaining time
            timer = new PomodoroTimer(timer.getMillisUntilFinished(), timer.getPhase());
            timer.start();
        }
        timer.toggleTimerIsRunning();
    }

    // stops the current pomodoro completely, dismissing any progress
    public void stopTimer(View v) {

        alarm.stop();
        // stop old timer
        if (timer != null) {
            timer.cancel();
            timer.toggleTimerIsRunning();
            // restart
            timer = new PomodoroTimer(POMODORO_DURATION, Phase.POMODORO);
        }
        pomodoros = 0;
        ratingBar.setRating(0);
        progressBar.setProgress(0);
        progressBar.setMax((int) POMODORO_DURATION);
        button.setText("Start");
        caption.setText("Pomodoro");
        textView.setText("25 : 00");
    }

    public void insert(String session, int count) {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DBEntry.COLUMN_NAME_SESSION, session);
        values.put(DBEntry.COLUMN_NAME_COUNT, count);
        database.insert(DBEntry.TABLE_NAME, null, values);
    }

    public void addSession(String session) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        ContentValues values = new ContentValues();
        int count = getCount(session);
        values.put(DBEntry.COLUMN_NAME_COUNT, count + 1);

        String selection = DBEntry.COLUMN_NAME_SESSION + " = ?";
        String[] selectionArgs = { session };

        database.update(
                DBEntry.TABLE_NAME,
                values,
                selection,
                selectionArgs
        );
    }

    public void delete(String session) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String selection = DBEntry.COLUMN_NAME_SESSION + " LIKE ?";
        String[] selectionArgs = { session };
        database.delete(DBEntry.TABLE_NAME, selection, selectionArgs);

    }

    public void queryAll() {
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String[] projection = {
                DBEntry._ID,
                DBEntry.COLUMN_NAME_SESSION,
                DBEntry.COLUMN_NAME_COUNT
        };

        String orderBy = DBEntry.COLUMN_NAME_SESSION + " DESC";

        try (Cursor cursor = database.query(
                DBEntry.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                orderBy
        )) {
            System.out.println(DatabaseUtils.dumpCursorToString(cursor));
        };
    }

    public int getCount(String session) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String[] projection = {
                DBEntry.COLUMN_NAME_COUNT
        };

        String selection = DBEntry.COLUMN_NAME_SESSION + " = ?";
        String[] selectionArgs = { session };

        try (Cursor cursor = database.query(
                DBEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        )) {
            cursor.moveToFirst();
            return cursor.getInt(cursor.getColumnIndex(DBEntry.COLUMN_NAME_COUNT));
        }
    }

    @Override
    protected void onDestroy() {
        dbHelper.close();
        super.onDestroy();
    }
}
