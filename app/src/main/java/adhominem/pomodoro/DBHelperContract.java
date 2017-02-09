package adhominem.pomodoro;

import android.provider.BaseColumns;

final class DBHelperContract {

    private DBHelperContract() {}

    static class DBEntry implements BaseColumns {
        static final String TABLE_NAME = "pomodoro";
        static final String COLUMN_NAME_SESSION = "session";
        static final String COLUMN_NAME_COUNT = "count";
    }
}
