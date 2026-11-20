import { describe,it,expect } from 'vitest'
import { SseDecoder } from './sse'
describe('SSE framing',()=>{
  it('preserves CRLF frames split at any character boundary',()=>{
    const frames:{event:string;data:string}[]=[];const parser=new SseDecoder(frame=>frames.push(frame))
    for(const c of 'event: delta\r\ndata: {"content":"需求"}\r\n\r\nevent: done\r\ndata: {}\r\n\r\n')parser.push(c)
    parser.finish();expect(frames).toEqual([{event:'delta',data:'{"content":"需求"}'},{event:'done',data:'{}'}])
  })
  it('joins multiline data and ignores heartbeats',()=>{
    const frames:{event:string;data:string}[]=[];const parser=new SseDecoder(frame=>frames.push(frame))
    parser.push(': keepalive\ndata: first\ndata: second\n\n');parser.finish();expect(frames).toEqual([{event:'message',data:'first\nsecond'}])
  })
})
