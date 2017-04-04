package adhominem.pomodoro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

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
        sessionCount = sessionGetCount(session);
        render();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public void fillSpinner() {
        // you need to have a list of data that you want the spinner to display
        List<String> spinnerArray = sessionGetAll();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, spinnerArray);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner sItems = (Spinner) findViewById(R.id.spinner);
        sItems.setAdapter(adapter);
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
                sessionCountIncrement(session);
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

    public void mute(View view) {
        AudioManager audioManager=(AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, AudioManager.FLAG_SHOW_UI,
                AudioManager.FLAG_PLAY_SOUND);
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
        fillSpinner();
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

    // Adds a new session to the database with a given count
    public void sessionAdd(String session, int count) {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DBEntry.COLUMN_NAME_SESSION, session);
        values.put(DBEntry.COLUMN_NAME_COUNT, count);
        database.insert(DBEntry.TABLE_NAME, null, values);
    }

    // Increments a given session's count by one
    public void sessionCountIncrement(String session) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        ContentValues values = new ContentValues();
        int count = sessionGetCount(session);
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

    public void sessionDelete(String session) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String selection = DBEntry.COLUMN_NAME_SESSION + " LIKE ?";
        String[] selectionArgs = { session };
        database.delete(DBEntry.TABLE_NAME, selection, selectionArgs);

    }

    public void sessionQueryAll() {
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

    // Returns an array containing all session names
    public List<String> sessionGetAll() {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        List<String> result;

        String[] projection = {
                DBEntry.COLUMN_NAME_SESSION
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
            int sessionIndex = cursor.getColumnIndex(DBEntry.COLUMN_NAME_SESSION);
            result = new ArrayList<>();
            while (cursor.moveToNext()) {
                result.add(cursor.getString(sessionIndex));
            }
        }

        return result;
    }

    public int sessionGetCount(String session) {
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

    public void promptForAddSession(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add a new session");

        // Set up the input
        final EditText input = new EditText(this);

        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String inputText = input.getText().toString();
                sessionAdd(inputText, 0);
                fillSpinner();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    public void promptForDeleteSession(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("You really want to delete session " + session + "?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        sessionDelete(session);
                        fillSpinner();
                        System.out.println("Session " + session + " has been deleted!");
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        // Create the AlertDialog object and show it
        builder.create().show();

    }
}
