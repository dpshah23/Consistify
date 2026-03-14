from rest_framework.routers import DefaultRouter
from .views import WorkoutSessionViewSet, RepViewSet
from .views import FitnessSessionViewSet

router = DefaultRouter()
router.register('sessions', WorkoutSessionViewSet, basename='session')
router.register('reps', RepViewSet, basename='rep')
router.register('fitness', FitnessSessionViewSet, basename='fitness')

urlpatterns = router.urls
