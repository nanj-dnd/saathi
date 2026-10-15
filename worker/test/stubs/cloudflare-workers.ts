// Stand-in for the Workers runtime module under Node, so Durable Object classes can be unit-tested.
export class DurableObject<Env = unknown> {
  constructor(
    protected ctx: DurableObjectState,
    protected env: Env,
  ) {}
}
