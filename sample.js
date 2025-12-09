import ModelClient, {isUnexpected} from "@azure-rest/ai-inference";
import {AzureKeyCredential} from "@azure/core-auth";

const token = process.env.GITHUB_TOKEN || "your-github-token";
const endpoint = "https://models.github.ai/inference";
const model = "openai/gpt-5-nano";

export async function main() {
    const client = ModelClient(
        "https://models.github.ai/inference",
        new AzureKeyCredential(token)
    );

    const response = await client.path("/chat/completions").post({
        body: {
            messages: [
                {role: "system", content: ""},
                {role: "user", content: "Can you explain the basics of machine learning?"}
            ],
            model: "meta/Meta-Llama-3.1-405B-Instruct",
            temperature: 0.8,
            max_tokens: 2048,
            top_p: 0.1
        }
    });

    if (isUnexpected(response)) {
        throw response.body.error;
    }
    console.log(response.body.choices[0].message.content);
}

main().catch((err) => {
    console.error("The sample encountered an error:", err);
});
