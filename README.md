#  bookGinnie

This project is an Android-based mobile application developed with the support of TÜBİTAK 2209-A. It offers users personalized book recommendations and generates age-appropriate original fairy tales, which are also presented in audio format.

##  Project Features

###  Personalized Book Recommendation System
- **Dataset**: 28 million book interaction records
- **Model**: Trained using the ALS (Alternating Least Squares) algorithm with PySpark
- **Service**: Served as a live REST API using Flask on AWS EC2 (t2.micro)

###  Fairy Tale Generation
- **Model**: Fine-tuned LLM using QLoRA, hosted on Hugging Face Spaces
- **Input**: Tales generated based on user-provided keywords, age, and preferences
- **Service**: Delivered via a FastAPI-based API

###  Text-to-Speech Narration
- **Technology**: Integrated with Microsoft Azure Text-to-Speech (TTS)
- **Purpose**: Allows children to listen to the generated fairy tales in audio format, enhancing accessibility and engagement

##  Platform
- **Mobile Application**: Android (Kotlin, Jetpack, MVVM architecture)
- **API Services**: Python (Flask & FastAPI)
