from django.contrib import admin
from .models import WorkoutSession, Rep
from .models import FitnessSession


@admin.register(WorkoutSession)
class WorkoutSessionAdmin(admin.ModelAdmin):
    list_display = ['id', 'started_at', 'ended_at', 'squat_count', 'pushup_count', 'duration_seconds']
    list_filter = ['started_at']
    readonly_fields = ['started_at']


@admin.register(Rep)
class RepAdmin(admin.ModelAdmin):
    list_display = ['id', 'session', 'exercise_type', 'rep_number', 'recorded_at']
    list_filter = ['exercise_type', 'recorded_at']


@admin.register(FitnessSession)
class FitnessSessionAdmin(admin.ModelAdmin):
    list_display = [
        'id', 'started_at', 'total_steps', 'distance_meters',
        'avg_speed_kmh', 'avg_heart_rate', 'calories_burned',
        'activity_type', 'duration_seconds',
    ]
    list_filter = ['activity_type', 'started_at']
    readonly_fields = ['started_at']
