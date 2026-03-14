from django.urls import path
from .views import process_daily_gamification, fetch_user_gamification

urlpatterns = [
    path('process/', process_daily_gamification, name='process_daily_gamification'),
    path('status/', fetch_user_gamification, name='fetch_user_gamification'),
]