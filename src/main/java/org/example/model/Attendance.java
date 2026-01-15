package org.example.model;

import java.time.Duration;
import java.time.LocalDateTime;

public class Attendance {
    private long id;
    private long userId;
    private String workDate;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;

    // pause fields
    private LocalDateTime pauseCheckInTime;
    private LocalDateTime pauseCheckOutTime;
    private boolean isOnPause;

    public Attendance() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getWorkDate() { return workDate; }
    public void setWorkDate(String workDate) { this.workDate = workDate; }

    public LocalDateTime getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDateTime checkIn) { this.checkIn = checkIn; }

    public LocalDateTime getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDateTime checkOut) { this.checkOut = checkOut; }

    public LocalDateTime getPauseCheckInTime() { return pauseCheckInTime; }
    public void setPauseCheckInTime(LocalDateTime pauseCheckInTime) { this.pauseCheckInTime = pauseCheckInTime; }

    public LocalDateTime getPauseCheckOutTime() { return pauseCheckOutTime; }
    public void setPauseCheckOutTime(LocalDateTime pauseCheckOutTime) { this.pauseCheckOutTime = pauseCheckOutTime; }

    public boolean isOnPause() { return isOnPause; }
    public void setOnPause(boolean onPause) { isOnPause = onPause; }

    // returns total worked minutes accounting for pause duration. If check-in/out missing, returns 0.
    public long getWorkedMinutes() {
        if (checkIn == null || checkOut == null) return 0;
        try {
            long total = Duration.between(checkIn, checkOut).toMinutes();
            long pause = 0;
            if (pauseCheckInTime != null && pauseCheckOutTime != null) {
                pause = Duration.between(pauseCheckInTime, pauseCheckOutTime).toMinutes();
            }
            long worked = total - pause;
            return worked < 0 ? 0 : worked;
        } catch (Exception e) {
            return 0;
        }
    }

    // formatted worked time as H:MM
    public String getWorkedTimeFormatted() {
        long mins = getWorkedMinutes();
        long h = mins / 60;
        long m = mins % 60;
        return String.format("%d:%02d", h, m);
    }
}
