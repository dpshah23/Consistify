from django.db import models


class WorkoutSession(models.Model):
    started_at = models.DateTimeField(auto_now_add=True)
    ended_at = models.DateTimeField(null=True, blank=True)
    squat_count = models.IntegerField(default=0)
    pushup_count = models.IntegerField(default=0)
    duration_seconds = models.IntegerField(default=0)

    def __str__(self):
        return f"Session {self.id} — {self.started_at.strftime('%Y-%m-%d %H:%M')} | squats={self.squat_count} pushups={self.pushup_count}"


class Rep(models.Model):
    EXERCISE_CHOICES = [
        ('squat', 'Squat'),
        ('pushup', 'Push-up'),
    ]

    session = models.ForeignKey(
        WorkoutSession,
        on_delete=models.CASCADE,
        related_name='reps',
        null=True,
        blank=True,
    )
    exercise_type = models.CharField(max_length=10, choices=EXERCISE_CHOICES)
    rep_number = models.IntegerField()
    recorded_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.exercise_type} rep #{self.rep_number} (session {self.session_id})"


class FitnessSession(models.Model):
    ACTIVITY_CHOICES = [
        ('STILL',   'Still'),
        ('WALKING', 'Walking'),
        ('RUNNING', 'Running'),
        ('UNKNOWN', 'Unknown'),
    ]

    started_at       = models.DateTimeField(auto_now_add=True)
    ended_at         = models.DateTimeField(null=True, blank=True)
    total_steps      = models.IntegerField(default=0)
    distance_meters  = models.FloatField(default=0.0)
    avg_speed_kmh    = models.FloatField(default=0.0)
    avg_heart_rate   = models.IntegerField(default=0)
    calories_burned  = models.IntegerField(default=0)
    duration_seconds = models.IntegerField(default=0)
    activity_type    = models.CharField(
        max_length=10, choices=ACTIVITY_CHOICES, default='UNKNOWN')

    class Meta:
        ordering = ['-started_at']

    def __str__(self):
        return (f"Fitness {self.id} — "
                f"{self.started_at.strftime('%Y-%m-%d %H:%M')} | "
                f"steps={self.total_steps} type={self.activity_type}")
