// Deterministic rendering of the authored branding/bob-mark.svg geometry. No font dependency.
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let root = URL(fileURLWithPath: CommandLine.arguments[1])
let size = 1024
let context = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: size*4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
context.setFillColor(red: 31/255, green: 95/255, blue: 224/255, alpha: 1)
context.fill(CGRect(x: 0, y: 0, width: size, height: size))
context.translateBy(x: 0, y: CGFloat(size))
context.scaleBy(x: CGFloat(size)/108, y: -CGFloat(size)/108)
context.translateBy(x: 26, y: 27)
context.scaleBy(x: 2.25, y: 2.25)
let b = CGMutablePath()
b.move(to: CGPoint(x:4,y:1.2)); b.addLine(to: CGPoint(x:9,y:1.2)); b.addLine(to: CGPoint(x:9,y:8.4))
b.addCurve(to: CGPoint(x:13.2,y:7.2), control1: CGPoint(x:10.25,y:7.6), control2: CGPoint(x:11.65,y:7.2))
b.addCurve(to: CGPoint(x:21,y:15), control1: CGPoint(x:17.5,y:7.2), control2: CGPoint(x:21,y:10.7))
b.addCurve(to: CGPoint(x:13.2,y:22.8), control1: CGPoint(x:21,y:19.3), control2: CGPoint(x:17.5,y:22.8))
b.addCurve(to: CGPoint(x:4,y:13.6), control1: CGPoint(x:8.12,y:22.8), control2: CGPoint(x:4,y:18.68))
b.closeSubpath(); b.addEllipse(in: CGRect(x:9.9,y:11.7,width:6.6,height:6.6))
context.addPath(b); context.setFillColor(CGColor(gray:1,alpha:1)); context.drawPath(using:.eoFill)
let image = context.makeImage()!
for name in ["AppIcon.appiconset/BobIcon.png", "BobMark.imageset/BobMark.png"] {
 let url = root.appendingPathComponent("Bob/Assets.xcassets/"+name)
 let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
 CGImageDestinationAddImage(destination,image,nil); CGImageDestinationFinalize(destination)
}
