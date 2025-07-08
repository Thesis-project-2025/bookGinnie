#  bookGinnie

Bu proje, TÜBİTAK 2209-A desteğiyle geliştirilen bir Android tabanlı mobil uygulamadır. Kullanıcılara hem kişiselleştirilmiş kitap önerileri sunmakta hem de yaşa uygun özgün masallar üretmekte ve üretilen masalları sesli şekilde sunmaktadır.

##  Proje Özellikleri

###  Kişisel Kitap Öneri Sistemi
- **Veri**: 28 milyon kitap etkileşimi verisi
- **Model**: PySpark kullanılarak ALS (Alternating Least Squares) algoritması ile eğitim
- **Servis**: Flask tabanlı REST API ile AWS EC2 (t2.micro) üzerinde canlı servis

###  Masal Üretimi
- **Model**: Hugging Face Spaces üzerinde barındırılan, QLoRA ile ince ayarlanmış dil modeli (LLM)
- **Girdi**: Kullanıcının belirttiği anahtar kelimeler, yaş ve diğer tercihlere göre masal üretimi
- **Servis**: FastAPI ile üretim API’si olarak sunuldu

###  Seslendirme
- **Teknoloji**: Azure Text-to-Speech (TTS) entegrasyonu
- **Amaç**: Üretilen masalların çocuklar tarafından sesli olarak dinlenebilmesini sağlamak

##  Platform
- **Mobil Uygulama**: Android (Kotlin, Jetpack, MVVM mimarisi)
- **API Servisleri**: Python (Flask & FastAPI)

