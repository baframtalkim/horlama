# Horlama Önleyici

Termux ile GitHub'a gönderme:

    pkg install git
    cd horlama
    git init
    git branch -M main
    git add .
    git commit -m "ilk surum"
    git remote add origin https://github.com/KULLANICI/REPO.git
    git push -u origin main

Şifre yerine GitHub "Personal Access Token" kullan.
Sonra GitHub'da Actions sekmesi -> Build APK -> en alttaki "horlama-apk" dosyasını indir.
