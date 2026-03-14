from django.shortcuts import render
from django.http import JsonResponse
import random
import hashlib

from accounts.models import UserCustom
# Create your views here.


def signup(request):

    if request.method=="POST":

        try:
            existing_user = UserCustom.objects.get(email=request.POST.get("email"))
            return JsonResponse({"error":"Email already exists"}, status=400)
        except UserCustom.DoesNotExist:
            pass
        password_hash=hashlib.sha256(request.POST.get("password").encode()).hexdigest()
        userobj={
            "id":random.randint(1000,9999)+request.POST.get("username"),
            "email":request.POST.get("email"),
            "password":password_hash,
            "username":request.POST.get("username")
        }

        UserCustom.objects.create(**userobj)
        userobj.pop("password")
        
        return JsonResponse({"message":"User created successfully","user":userobj})

        
def login(request):

    if request.method=="POST":
        try:
            user = UserCustom.objects.get(email=request.POST.get("email"))
            if user.validate_password(request.POST.get("password")):
                return JsonResponse({"message":"Login successful","user":{"id":user.id,"email":user.email,"username":user.username}})
            else:
                return JsonResponse({"error":"Invalid password"}, status=400)
        except UserCustom.DoesNotExist:
            return JsonResponse({"error":"User not found"}, status=404)
        

def profile(request):
    if request.method=="GET":
        user_id = request.GET.get("user_id")
        try:
            user = UserCustom.objects.get(id=user_id)
            return JsonResponse({"user":{"id":user.id,"email":user.email,"username":user.username}})
        except UserCustom.DoesNotExist:
            return JsonResponse({"error":"User not found"}, status=404)