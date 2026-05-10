# Blocked
Worker: moe
Blocked At: 2026-05-10T17:39:06.451214+00:00
Reason: litellm.ContextWindowExceededError: litellm.BadRequestError: ContextWindowExceededError: Hosted_vllmException - {"error":{"message":"This model's maximum context length is 16384 tokens. However, you requested 8192 output tokens and your prompt contains at least 8193 input tokens, for a total of at least 16385 tokens. Please reduce the length of the input prompt or the number of requested output tokens. (parameter=input_tokens, value=8193)","type":"BadRequestError","param":"input_tokens","code":400}}
