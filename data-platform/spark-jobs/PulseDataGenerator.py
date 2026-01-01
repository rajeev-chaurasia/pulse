import time
import random
import uuid
import sys
from kafka import KafkaProducer
import swipe_pb2

# Configuration
BOOTSTRAP_SERVERS = 'localhost:9094'
TOPIC = 'swipes'

def generate_swipe():
    swipe = swipe_pb2.SwipeRequest()
    swipe.user_id = str(uuid.uuid4())
    swipe.target_id = str(uuid.uuid4())
    swipe.is_like = random.choice([True, False])
    swipe.timestamp = int(time.time() * 1000)
    return swipe

def produce_swipes(num_swipes=500):
    try:
        producer = KafkaProducer(bootstrap_servers=BOOTSTRAP_SERVERS)
        print(f"🚀 Producing {num_swipes} swipes to {TOPIC} on {BOOTSTRAP_SERVERS}...")
        
        for i in range(num_swipes):
            swipe = generate_swipe()
            serialized_swipe = swipe.SerializeToString()
            producer.send(TOPIC, serialized_swipe)
            if i > 0 and i % 50 == 0:
                print(f"Sent {i} swipes...")
                sys.stdout.flush()
            time.sleep(0.01)
            
        producer.flush()
        print(f"✅ Successfully produced {num_swipes} events.")
        producer.close()
    except Exception as e:
        print(f"❌ Error: {e}")

if __name__ == "__main__":
    produce_swipes()
