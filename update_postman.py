import json
import os

with open("trackit.postman_collection.json", "r", encoding="utf-8") as f:
    collection = json.load(f)

inference_folder = {
    "name": "Inference Service API",
    "description": "Endpoints to test the Python Inference Service directly (Port 8001).",
    "item": [
        {
            "name": "Health Check",
            "request": {
                "method": "GET",
                "header": [],
                "url": {
                    "raw": "http://localhost:8001/health",
                    "host": ["localhost"],
                    "port": "8001",
                    "path": ["health"]
                },
                "description": "Checks if the inference service is up and the model is loaded."
            },
            "response": []
        },
        {
            "name": "Happy Path: Both ECG and PPG",
            "event": [
                {
                    "listen": "prerequest",
                    "script": {
                        "exec": [
                            "pm.variables.set('ecg_array', JSON.stringify(new Array(7680).fill(0.123)));",
                            "pm.variables.set('ppg_array', JSON.stringify(new Array(1920).fill(0.456)));"
                        ],
                        "type": "text/javascript"
                    }
                }
            ],
            "request": {
                "method": "POST",
                "header": [{"key": "Content-Type", "value": "application/json"}],
                "body": {
                    "mode": "raw",
                    "raw": "{\n    \"ecg\": {{ecg_array}},\n    \"ppg\": {{ppg_array}}\n}"
                },
                "url": {
                    "raw": "http://localhost:8001/infer",
                    "host": ["localhost"],
                    "port": "8001",
                    "path": ["infer"]
                },
                "description": "Valid request containing both 30 seconds of ECG and 30 seconds of PPG."
            },
            "response": []
        },
        {
            "name": "Happy Path: ECG Only",
            "event": [
                {
                    "listen": "prerequest",
                    "script": {
                        "exec": [
                            "pm.variables.set('ecg_array', JSON.stringify(new Array(7680).fill(0.123)));"
                        ],
                        "type": "text/javascript"
                    }
                }
            ],
            "request": {
                "method": "POST",
                "header": [{"key": "Content-Type", "value": "application/json"}],
                "body": {
                    "mode": "raw",
                    "raw": "{\n    \"ecg\": {{ecg_array}}\n}"
                },
                "url": {
                    "raw": "http://localhost:8001/infer",
                    "host": ["localhost"],
                    "port": "8001",
                    "path": ["infer"]
                },
                "description": "Valid request containing only ECG data (supported mode)."
            },
            "response": []
        },
        {
            "name": "Edge Case: Missing Both Signals",
            "request": {
                "method": "POST",
                "header": [{"key": "Content-Type", "value": "application/json"}],
                "body": {
                    "mode": "raw",
                    "raw": "{}"
                },
                "url": {
                    "raw": "http://localhost:8001/infer",
                    "host": ["localhost"],
                    "port": "8001",
                    "path": ["infer"]
                },
                "description": "Invalid request omitting both signals. Expect 400 Bad Request."
            },
            "response": []
        },
        {
            "name": "Edge Case: Incorrect ECG Length",
            "request": {
                "method": "POST",
                "header": [{"key": "Content-Type", "value": "application/json"}],
                "body": {
                    "mode": "raw",
                    "raw": "{\n    \"ecg\": [0.0, 1.0, 2.0]\n}"
                },
                "url": {
                    "raw": "http://localhost:8001/infer",
                    "host": ["localhost"],
                    "port": "8001",
                    "path": ["infer"]
                },
                "description": "Invalid request with wrong ECG length. Expect 400 Bad Request."
            },
            "response": []
        },
        {
            "name": "Edge Case: Incorrect PPG Length",
            "event": [
                {
                    "listen": "prerequest",
                    "script": {
                        "exec": [
                            "pm.variables.set('ecg_array', JSON.stringify(new Array(7680).fill(0.123)));"
                        ],
                        "type": "text/javascript"
                    }
                }
            ],
            "request": {
                "method": "POST",
                "header": [{"key": "Content-Type", "value": "application/json"}],
                "body": {
                    "mode": "raw",
                    "raw": "{\n    \"ecg\": {{ecg_array}},\n    \"ppg\": [0.0, 1.0, 2.0]\n}"
                },
                "url": {
                    "raw": "http://localhost:8001/infer",
                    "host": ["localhost"],
                    "port": "8001",
                    "path": ["infer"]
                },
                "description": "Invalid request with correct ECG but wrong PPG length. Expect 400 Bad Request."
            },
            "response": []
        }
    ]
}

found = False
for item in collection.get('item', []):
    if item.get('name') == "Inference Service API":
        item['item'] = inference_folder['item']
        found = True
        break

if not found:
    collection['item'].append(inference_folder)

with open("trackit.postman_collection.json", "w", encoding="utf-8") as f:
    json.dump(collection, f, indent=4)
print("Postman collection updated successfully.")
