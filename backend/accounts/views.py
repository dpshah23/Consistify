from django.shortcuts import render
from django.http import JsonResponse
import random
import hashlib
import uuid

from accounts.models import UserCustom
from django.views.decorators.csrf import csrf_exempt
# Create your views here.

import json

@csrf_exempt
def signup(request):
    if request.method=="POST":
        data = request.POST
        if not data:
            try:
                data = json.loads(request.body)
            except Exception:
                data = {}

        email = data.get("email")
        password = data.get("password")
        username = data.get("username")

        if not email or not password or not username:
            return JsonResponse({"error": "Missing required fields"}, status=400)

        try:
            existing_user = UserCustom.objects.get(email=email)
            return JsonResponse({"error":"Email already exists"}, status=400)
        except UserCustom.DoesNotExist:
            pass

        password_hashed = hashlib.sha256(password.encode()).hexdigest()
        user_uuid = uuid.uuid4()
        userobj = {
            "id": user_uuid,
            "email": email,
            "password_hash": password_hashed,
            "username": username
        }

        UserCustom.objects.create(**userobj)
        userobj.pop("password_hash")
        userobj["id"] = str(user_uuid)
        
        return JsonResponse({"message":"User created successfully","user":userobj})

@csrf_exempt
def login(request):
    if request.method=="POST":
        data = request.POST
        if not data:
            try:
                data = json.loads(request.body)
            except Exception:
                data = {}

        email = data.get("email")
        password = data.get("password")

        if not email or not password:
            return JsonResponse({"error": "Missing email or password"}, status=400)

        try:
            user = UserCustom.objects.get(email=email)
            if user.validate_password(password):
                return JsonResponse({"message":"Login successful","user":{"id":user.id,"email":user.email,"username":user.username}})
            else:
                return JsonResponse({"error":"Invalid password"}, status=400)
        except UserCustom.DoesNotExist:
            return JsonResponse({"error":"User not found"}, status=404)
        

@csrf_exempt
def profile(request, user_id):
    if request.method=="GET":
        try:
            user = UserCustom.objects.get(id=user_id)
            return JsonResponse({"user":{"id":user.id,"email":user.email,"username":user.username}})
        except UserCustom.DoesNotExist:
            return JsonResponse({"error":"User not found"}, status=404)