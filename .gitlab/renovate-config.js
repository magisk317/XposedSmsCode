module.exports = {
  platform: "gitlab",
  endpoint: process.env.CI_API_V4_URL || "https://gitlab.com/api/v4/",
  repositories: ["magisk731/XposedSmsCode"],
  onboarding: false,
  requireConfig: "required",
  allowedUnsafeExecutions: ["gradleWrapper"],
  gitAuthor: "magisk317 <93979778+magisk317@users.noreply.github.com>",
};
