package adhominem.pomodoro;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
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

@SuppressLint("SetTextI18n")
public class MainActivity extends Activity implements AdapterView.OnItemSelectedListener {

    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = 60000;
    private static final long POMODORO_DURATION = ONE_MINUTE * 25;
    private static final long SHORT_BREAK_DURATION = ONE_MINUTE * 5;
    private static final long LONG_BREAK_DURATION = ONE_MINUTE * 15;

    // App state
    private PomodoroTimer timer;

    private Button startButton;
    private ProgressBar progressBar;
    private TextView phaseDisplay;
    private RatingBar ratingBar;
    private TextView timeDisplay;
    private TextView pomodoroStatsText;

    private int pomodoros;
    private String session;
    private int sessionCount;
    private Ringtone alarm;
    private DBHelper dbHelper;
    private boolean timerIsRunning;
    private enum Phase {POMODORO, SHORT_BREAK, LONG_BREAK }

    private String phaseDisplayString;
    private String startButtonString;
    private String timeDisplayString;

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        session = String.valueOf(parent.getItemAtPosition(position));
        sessionCount = getCount(session);
        render();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    class PomodoroTimer extends CountDownTimer {

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

        void toggleTimerIsRunning() {
            timerIsRunning = !timerIsRunning;
        }

        Phase getPhase() {
            return phase;
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onTick(long millisUntilFinished) {

            // This stores the remaining time. If timer is stopped, new total will be used
            this.millisUntilFinished = millisUntilFinished;
            timeDisplay.setText(String.format("%02d : %02d", millisUntilFinished / ONE_MINUTE,
                    millisUntilFinished % ONE_MINUTE / ONE_SECOND));
            progressBar.incrementProgressBy((int) ONE_SECOND);
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onFinish() {
            timerIsRunning = false;

            switchPhase();
            render();

            timeDisplay.setText(String.format("%02d : %02d", millisUntilFinished / ONE_MINUTE,
                    millisUntilFinished % ONE_MINUTE / ONE_SECOND));
        }

        void switchPhase() {
            toggleDoNotDisturb();
            progressBar.setProgress(0);
            alarm.play();
            if (phase == Phase.POMODORO) {
                pomodoros += 1;
                addSession(session);
                ++sessionCount;
                // determine next phase
                // take a break
                if (pomodoros == 4) {
                    phase = Phase.LONG_BREAK;
                    millisUntilFinished = LONG_BREAK_DURATION;
                    progressBar.setMax((int) (LONG_BREAK_DURATION));
                    phaseDisplayString = "Long break";
                    startButtonString = "Start long break";
                } else {
                    phase = Phase.SHORT_BREAK;
                    millisUntilFinished = SHORT_BREAK_DURATION;
                    progressBar.setMax((int) (SHORT_BREAK_DURATION));
                    phaseDisplayString = "Short break";
                    startButtonString = "Start short break";
                }
            } else {
                phase = Phase.POMODORO;
                millisUntilFinished = POMODORO_DURATION;
                progressBar.setMax((int) (POMODORO_DURATION));
                phaseDisplayString = "Pomodoro";
                startButtonString = "Start";
            }
        }
    }

    void toggleDoNotDisturb() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        View decorView = getWindow().getDecorView();

        int interruptionFilter = notificationManager.getCurrentInterruptionFilter(), visibility;
        if (interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
            visibility = View.SYSTEM_UI_FLAG_VISIBLE;
        } else {
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
            visibility = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        }

        decorView.setSystemUiVisibility(visibility);
        notificationManager.setInterruptionFilter(interruptionFilter);
    }

    void doNotDisturb(boolean value) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        View decorView = getWindow().getDecorView();

        int interruptionFilter, visibility;
        if (value) {
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
            visibility =  View.SYSTEM_UI_FLAG_LOW_PROFILE;
        } else {
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
            visibility =  View.SYSTEM_UI_FLAG_VISIBLE;
        }

        decorView.setSystemUiVisibility(visibility);
        notificationManager.setInterruptionFilter(interruptionFilter);
    }

    void render() {
        phaseDisplay.setText(phaseDisplayString);
        startButton.setText(startButtonString);
        ratingBar.setRating(pomodoros);
        pomodoroStatsText.setText("Current pomodoros spent on\n" + session + ": " + sessionCount);
        startButton.setText(timerIsRunning ? "Pause" : "Start");
        timeDisplay.setText(timeDisplayString);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        startButton = (Button) findViewById(R.id.startButton);
        timeDisplay = (TextView) findViewById(R.id.timeDisplay);
        ratingBar = (RatingBar) findViewById(R.id.ratingBar);
        phaseDisplay = (TextView) findViewById(R.id.phaseDisplay);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setMax((int) POMODORO_DURATION);
        pomodoroStatsText = (TextView) findViewById(R.id.pomodoroStatsText);
        dbHelper = new DBHelper(getApplicationContext());
        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(this);
        pomodoros = 0;
        phaseDisplayString = "Pomodoro";
        startButtonString = "Start";
        timeDisplayString = "25 : 00";
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        alarm = RingtoneManager.getRingtone(getApplicationContext(), notification);
    }

    @SuppressLint("DefaultLocale")
    public void toggleTimer(View v) {
        alarm.stop();
        toggleDoNotDisturb();

        if (timer == null) {
            timer = new PomodoroTimer(POMODORO_DURATION, Phase.POMODORO);
            timer.start();
            timerIsRunning = true;
        } else if (timerIsRunning) {
            timer.cancel();
            timeDisplayString = String.format("%02d : %02d", timer.millisUntilFinished / ONE_MINUTE,
                    timer.millisUntilFinished % ONE_MINUTE / ONE_SECOND);
            timerIsRunning = false;
        } else {
            // get remaining time
            timer = new PomodoroTimer(timer.getMillisUntilFinished(), timer.getPhase());
            timeDisplayString = String.format("%02d : %02d", timer.millisUntilFinished / ONE_MINUTE,
                    timer.millisUntilFinished % ONE_MINUTE / ONE_SECOND);
            Log.d("Main", "Timer resuming with " + timeDisplayString + " in phase " + timer.getPhase());
            timer.start();
            timerIsRunning = true;
        }

        Log.d("Main", "Toggling timer, running: " + timerIsRunning);

        render();
    }

    // stops the current pomodoro completely, dismissing any progress
    public void reset(View v) {

        Log.d("Main", "Timer reset");

        alarm.stop();
        doNotDisturb(false);

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
        startButtonString = "Start";
        phaseDisplayString = "Pomodoro";
        timeDisplayString = "25 : 00";

        render();
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
        }
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
