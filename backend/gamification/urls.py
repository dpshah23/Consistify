from django.urls import path
from .views import (
    process_daily_gamification, fetch_user_gamification,
    send_challenge, respond_challenge, submit_challenge_score,
    get_user_challenges, get_notifications, mark_notification_read
)

urlpatterns = [
    path('process/', process_daily_gamification, name='process_daily_gamification'),
    path('status/', fetch_user_gamification, name='fetch_user_gamification'),
    path('challenge/send/', send_challenge, name='send_challenge'),
    path('challenge/respond/', respond_challenge, name='respond_challenge'),
    path('challenge/submit-score/', submit_challenge_score, name='submit_challenge_score'),
    path('challenges/', get_user_challenges, name='get_user_challenges'),
    path('notifications/', get_notifications, name='get_notifications'),
    path('notifications/mark-read/', mark_notification_read, name='mark_notification_read'),
]