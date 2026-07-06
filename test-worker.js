export default {
  async fetch(request, env, ctx) {
    console.log("=== Request received ===");
    console.log("Method:", request.method);
    console.log("URL:", request.url);
    console.log("Headers:", Object.fromEntries(request.headers));
    
    const url = new URL(request.url);
    const params = new URLSearchParams(url.search);
    
    console.log("Query params:", Object.fromEntries(params));
    
    if (params.has("test")) {
      console.log("Test mode enabled");
      console.warn("This is a warning message");
      console.error("This is an error message");
      console.debug("This is a debug message");
      
      try {
        throw new Error("Test exception");
      } catch (e) {
        console.error("Caught exception:", e.message);
      }
    }
    
    console.log("=== Request processed ===");
    
    return new Response("Hello from Worker! Check your real-time logs.", {
      headers: { "Content-Type": "text/plain" },
    });
  },

  async scheduled(event, env, ctx) {
    console.log("=== CRON Triggered ===");
    console.log("Cron:", event.cron);
    console.log("Scheduled time:", event.scheduledTime);
    console.log("=== CRON Completed ===");
  },
};