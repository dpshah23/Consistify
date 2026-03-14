from django.urls import path
from .views import get_feed, create_post, like_post


urlpatterns = [
    path("feed/<int:page>/", get_feed,name="get_feed"),
    path("create/", create_post,name="create_post"),
    path("like/", like_post,name="like_post"),
]