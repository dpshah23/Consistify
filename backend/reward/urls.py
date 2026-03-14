from django.urls import path
from .views import list_items, redeem_item, redemption_history


urlpatterns = [
    path("store/", list_items,name="list_items"),
    path("redeem/", redeem_item,name="redeem_item"),
    path("history/", redemption_history,name="redemption_history"),
]