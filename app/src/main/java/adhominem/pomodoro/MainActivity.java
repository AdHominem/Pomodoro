package adhominem.pomodoro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;

import java.io.IOException;

@SuppressLint("SetTextI18n")
public class MainActivity extends Activity {

    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = 60000;
    private static final long POMODORO_DURATION = ONE_MINUTE * 25;
    private static final long SHORT_BREAK_DURATION = ONE_MINUTE * 5;
    private static final long LONG_BREAK_DURATION = ONE_MINUTE * 15;

    private MediaPlayer mediaPlayer;
    private PomodoroTimer timer;
    private Button button;
    private ProgressBar progressBar;
    private TextView caption;
    private RatingBar ratingBar;
    private TextView textView;
    private static final String TAG = "MainActivity";
    private int pomodoros = 0;
    private enum Phase {POMODORO, SHORT_BREAK, LONG_BREAK};
    private String captionString;
    private String buttonString;

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
            renderCaptions();

            textView.setText(String.format("%02d : %02d", millisUntilFinished / ONE_MINUTE,
                    millisUntilFinished % ONE_MINUTE / ONE_SECOND));
        }

        void switchPhase() {
            progressBar.setProgress(0);
            if (phase == Phase.POMODORO) {
                playSound();
                pomodoros += 1;
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


    void renderCaptions() {
        caption.setText(captionString);
        button.setText(buttonString);
        ratingBar.setRating(pomodoros);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.micnight);
        button = (Button) findViewById(R.id.button);
        textView = (TextView) findViewById(R.id.textView);
        ratingBar = (RatingBar) findViewById(R.id.ratingBar);
        caption = (TextView) findViewById(R.id.caption);
        progressBar = (ProgressBar) findViewById(R.id.progressBar3);
        progressBar.setMax((int) POMODORO_DURATION);
        DBHelper dbHelper = new DBHelper(getApplicationContext());
    }

    public void toggleTimer(View v) {
        muteSound();

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
        muteSound();
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

    public void muteSound() {
        mediaPlayer.stop();
    }

    public void playSound() {
        if (!mediaPlayer.isPlaying()){
            try {
                mediaPlayer.prepare();
            } catch (IllegalStateException | IOException exception) {
                Log.e(TAG, exception.getMessage());
            }
            mediaPlayer.seekTo(0);
            mediaPlayer.start();
        }
    }
}
