from rest_framework import mixins, viewsets
from .models import WorkoutSession, Rep
from .models import FitnessSession
from .serializers import WorkoutSessionSerializer, RepSerializer
from .serializers import FitnessSessionSerializer


class WorkoutSessionViewSet(
    mixins.CreateModelMixin,
    mixins.UpdateModelMixin,
    mixins.RetrieveModelMixin,
    mixins.ListModelMixin,
    viewsets.GenericViewSet,
):
    """
    POST   /api/sessions/       — start a new session
    PATCH  /api/sessions/{id}/  — finalize with counts + duration
    GET    /api/sessions/       — list all sessions
    GET    /api/sessions/{id}/  — retrieve one session (includes reps)
    """
    queryset = WorkoutSession.objects.all().order_by('-started_at')
    serializer_class = WorkoutSessionSerializer


class RepViewSet(
    mixins.CreateModelMixin,
    mixins.ListModelMixin,
    viewsets.GenericViewSet,
):
    """
    POST /api/reps/  — log a single rep in real time
    GET  /api/reps/  — list all reps
    """
    queryset = Rep.objects.all().order_by('-recorded_at')
    serializer_class = RepSerializer


class FitnessSessionViewSet(
    mixins.CreateModelMixin,
    mixins.UpdateModelMixin,
    mixins.RetrieveModelMixin,
    mixins.ListModelMixin,
    viewsets.GenericViewSet,
):
    """
    POST   /api/fitness/       — start a fitness session
    PATCH  /api/fitness/{id}/  — update steps, speed, heart rate, calories
    GET    /api/fitness/       — list all fitness sessions
    GET    /api/fitness/{id}/  — retrieve one session
    """
    queryset = FitnessSession.objects.all()
    serializer_class = FitnessSessionSerializer
