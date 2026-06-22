import websocket
import json
import time
import threading

def on_message(ws, message):
    print("RECEIVED:", json.loads(message))
    # Close after receiving the first message
    ws.close()

def on_error(ws, error):
    print("ERROR:", error)

def on_close(ws, close_status_code, close_msg):
    print("### closed ###")

def on_open(ws):
    print("CONNECTED")
    # Send a fake sample
    payload = {
        "channel": "ecg",
        "value": 0.42,
        "sampleIndex": 0
    }
    print("Sending:", payload)
    ws.send(json.dumps(payload))
    
    # Send another one to verify it doesn't crash
    time.sleep(1)
    # the server doesn't respond immediately for every sample unless it errors or triggers inference,
    # so we just close it after 2 seconds to not hang indefinitely.
    threading.Timer(2.0, ws.close).start()

if __name__ == "__main__":
    websocket.enableTrace(False)
    ws = websocket.WebSocketApp("ws://localhost:8080/trackit/ws/ecg-monitor",
                              on_open=on_open,
                              on_message=on_message,
                              on_error=on_error,
                              on_close=on_close)
    ws.run_forever()
