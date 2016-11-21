package adhominem.pomodoro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;

@SuppressLint("SetTextI18n")
public class MainActivity extends Activity {

    private MediaPlayer mediaPlayer;
    private TextView textView;
    private CountDownTimer timer;
    private static final String TAG = "MainActivity";
    private boolean timerIsRunning;
    private long total = 25 * 60000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.micnight);
        textView = (TextView) findViewById(R.id.textView);
        timer = new CountDownTimer(total, 60000) {

            @Override
            public void onTick(long millisUntilFinished) {

                // This stores the remaining time. If timer is stopped, new total will be used
                total = millisUntilFinished;
                textView.setText(String.valueOf(millisUntilFinished / 60000) + " Minutes left");
            }

            @Override
            public void onFinish() {
                mediaPlayer.start();
                textView.setText("Done!");
            }
        };
    }

    /**
     * Starts or pauses the timer
     * @param v
     */
    public void toggleTimer(View v) {
        if (timerIsRunning) {
            timer.cancel();
        } else {
            timer.start();
        }
    }

    public void toggleAudio() {
        if (mediaPlayer.isPlaying()){
            mediaPlayer.stop();
            try {
                mediaPlayer.prepare();
            } catch (IllegalStateException | IOException exception) {
                Log.e(TAG, exception.getMessage());
            }
            mediaPlayer.seekTo(0);
        }
    }
}
