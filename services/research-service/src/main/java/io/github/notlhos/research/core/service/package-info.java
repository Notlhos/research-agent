/**
 * Research run lifecycle.
 *
 * <p>Agent work is dispatched only after the state change that requested it is committed, so an agent never
 * answers for a state that was rolled back. If the process dies between commit and dispatch, the run looks
 * stuck and the recovery job re-dispatches it after {@code research.step-timeout}; responses are matched by
 * run id, step and attempt, which makes such duplicates harmless.
 *
 * <p>Runs are not started by the API request itself. They wait as {@code PENDING} and are admitted in creation
 * order while fewer than {@code research.max-active-runs} runs are active, which bounds concurrent LLM and
 * embedding work, and its cost, regardless of incoming traffic.
 */
package io.github.notlhos.research.core.service;
