from django.urls import path
from .views import signup, login, get_profile

urlpatterns = [
    path("signup/", signup, name="signup"),
    path("login/", login, name="login"),
    path("profile/<int:user_id>/", get_profile, name="profile"),
]