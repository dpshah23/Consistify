from django.urls import path
from .views import get_activity, log_activity, today, activity_history

urlpatterns = [
    path("log/", log_activity, name="log_activity"),
    path("today/", today, name="today_activity"),
    path("history/", activity_history, name="activity_history"),
    path("history/<int:page>/", activity_history, name="activity_history_paginated"),
    path("get_activity/", get_activity, name="get_activity"),
]
