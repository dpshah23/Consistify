from django.urls import path
from . import views

urlpatterns = [
    path('dashboard/', views.get_dashboard_summary, name='dashboard_summary'),
    path('weekly/', views.get_weekly_chart_data, name='weekly_chart_data'),
    path('leaderboard/', views.get_leaderboard, name='leaderboard'),
]