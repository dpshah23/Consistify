from rest_framework import serializers
from .models import WorkoutSession, Rep
from .models import FitnessSession


class RepSerializer(serializers.ModelSerializer):
    class Meta:
        model = Rep
        fields = ['id', 'session', 'exercise_type', 'rep_number', 'recorded_at']


class WorkoutSessionSerializer(serializers.ModelSerializer):
    reps = RepSerializer(many=True, read_only=True)

    class Meta:
        model = WorkoutSession
        fields = [
            'id', 'started_at', 'ended_at',
            'squat_count', 'pushup_count', 'duration_seconds',
            'reps',
        ]
        read_only_fields = ['started_at']


class FitnessSessionSerializer(serializers.ModelSerializer):
    class Meta:
        model = FitnessSession
        fields = [
            'id', 'started_at', 'ended_at',
            'total_steps', 'distance_meters', 'avg_speed_kmh',
            'avg_heart_rate', 'calories_burned', 'duration_seconds',
            'activity_type',
        ]
        read_only_fields = ['started_at']
