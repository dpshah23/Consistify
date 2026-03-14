from django.urls import path
from .views import get_user_stats, update_xp, get_streak



urlpatterns = [
    path("stats/", get_user_stats,name="get_user_stats"),
    path("update-xp/", update_xp,name="update_xp"),
    path("streak/", get_streak,name="get_streak"),
]