from django.urls import path
from .views import get_rewards, redeem_reward, user_redemptions

urlpatterns = [
    path('list/', get_rewards, name='get_rewards'),
    path('redeem/', redeem_reward, name='redeem_reward'),
    path('history/', user_redemptions, name='user_redemptions'),
]