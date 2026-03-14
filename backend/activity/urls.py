from webbrowser import get

from django.urls import path
from .views import get_activity, log_activity, today_activity, activity_history,get_activity


urlpatterns = [
    path("log/", log_activity, name="log_activity"),
    path("today/", today_activity, name="today_activity"),
    path("history/", activity_history, name="activity_history"),
    path("history/<int:page>/", activity_history, name="activity_history_paginated"),
    path("get_activity/", get_activity, name="get_activity"),
]