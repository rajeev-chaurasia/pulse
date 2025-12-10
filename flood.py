import grpc
import time
import uuid
import random
import threading
from faker import Faker
import swipe_pb2
import swipe_pb2_grpc

# CONFIGURATION
TARGET = 'localhost:9090'
THREADS = 20              # More threads = More chaos
DURATION = 60             # Run for 60 seconds
POPULATION_SIZE = 1000    # How many active users in the system?

# SETUP
fake = Faker()
request_count = 0
match_count = 0

# 1. Generate the "World" (Persistent Users)
# We do this so 'Alice' is always the same UUID throughout the test
print(f"🌍 Generating population of {POPULATION_SIZE} fake users...")
POPULATION = []
for _ in range(POPULATION_SIZE):
    POPULATION.append({
        "id": str(uuid.uuid4()),
        "name": fake.name()
    })

# The "Influencer" (Someone everyone wants)
INFLUENCER = POPULATION[0]
print(f"🌟 The 'Influencer' is: {INFLUENCER['name']} ({INFLUENCER['id']})")

def send_swipe(stub, user_a, user_b, is_like=True):
    """Helper to send a single gRPC swipe"""
    try:
        stub.Swipe(swipe_pb2.SwipeRequest(
            user_id=user_a['name'],  # Send name
            target_id=user_b['name'],
            is_like=is_like,
            timestamp=int(time.time()*1000)
        ))
        return True
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

def chaos_thread(stop_event):
    global request_count, match_count

    channel = grpc.insecure_channel(TARGET)
    stub = swipe_pb2_grpc.SwipeServiceStub(channel)

    while not stop_event.is_set():
        scenario = random.choice(['serial', 'triangle', 'influencer', 'match'])

        # Pick random actors
        actor = random.choice(POPULATION)
        target = random.choice(POPULATION)

        if scenario == 'serial':
            # SCENARIO: The Serial Swiper
            # One person swipes right on 5 random people rapidly
            for _ in range(5):
                t = random.choice(POPULATION)
                if t['id'] == actor['id']: continue
                send_swipe(stub, actor, t, True)
                request_count += 1

        elif scenario == 'triangle':
            # SCENARIO: The Love Triangle (A->B, B->C, C->A) - Drama, no matches
            p1 = random.choice(POPULATION)
            p2 = random.choice(POPULATION)
            p3 = random.choice(POPULATION)

            send_swipe(stub, p1, p2, True) # Alice -> Bob
            send_swipe(stub, p2, p3, True) # Bob -> Charlie
            send_swipe(stub, p3, p1, True) # Charlie -> Alice
            request_count += 3

        elif scenario == 'influencer':
            # SCENARIO: The Influencer (Hot Key Problem)
            # 10 Random people swipe right on the Influencer
            for _ in range(10):
                fan = random.choice(POPULATION)
                send_swipe(stub, fan, INFLUENCER, True)
                request_count += 1

        elif scenario == 'match':
            # SCENARIO: True Love (Reciprocal)
            # A likes B, then B likes A (Triggers Flink Match)
            if actor['id'] == target['id']: continue

            # A -> B
            send_swipe(stub, actor, target, True)
            # B -> A
            send_swipe(stub, target, actor, True)

            request_count += 2
            match_count += 1 # We expect 1 match here

        # Sleep slightly to prevent DOS-ing your own laptop (remove for max speed)
        # time.sleep(0.01)

    channel.close()

def main():
    print(f"🌊 UNLEASHING CHAOS on {TARGET} with {THREADS} threads...")
    stop_event = threading.Event()
    threads = []

    for _ in range(THREADS):
        t = threading.Thread(target=chaos_thread, args=(stop_event,))
        t.daemon = True
        t.start()
        threads.append(t)

    start_time = time.time()
    try:
        while time.time() - start_time < DURATION:
            time.sleep(1)
            elapsed = time.time() - start_time
            eps = request_count / elapsed
            print(f"🚀 Speed: {int(eps)} swipes/sec | Total Swipes: {request_count} | Expected Matches: {match_count}")

    except KeyboardInterrupt:
        print("\n🛑 Stopping the madness...")

    stop_event.set()
    for t in threads:
        t.join()

    print("✅ Simulation Complete.")

if __name__ == '__main__':
    main()