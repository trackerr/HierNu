package nl.hiertoen.app.motion

/** Statusmodel §5.1. */
enum class MotionState {
    IDLE,
    MOVING,
    SLOW,
    STOP_CANDIDATE,
    STILL,
    PAUSED,
    GPS_UNRELIABLE,
}
