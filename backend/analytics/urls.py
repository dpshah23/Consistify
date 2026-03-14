from django.urls import path
from .views import leaderboard, user_statistics

urlpatterns = [
    path("leaderboard/", leaderboard,name="leaderboard"),
    path("user-stats/", user_statistics,name="user_statistics"),
]